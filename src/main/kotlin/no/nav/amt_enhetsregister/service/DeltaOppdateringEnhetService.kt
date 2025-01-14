package no.nav.amt_enhetsregister.service

import no.nav.amt_enhetsregister.client.BronnoysundClient
import no.nav.amt_enhetsregister.client.EnhetOppdatering
import no.nav.amt_enhetsregister.repository.DeltaOppdateringProgresjonRepository
import no.nav.amt_enhetsregister.repository.type.EnhetType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeltaOppdateringEnhetService(
	private val enhetService: EnhetService,
	private val deltaOppdateringRepository: DeltaOppdateringProgresjonRepository,
	private val bronnoysundClient: BronnoysundClient
) {

	companion object {
		const val OPPDATERINGER_SIZE = 500
		const val UKJENT_VIRKSOMHET_NR = "999999999"
	}

	private val log = LoggerFactory.getLogger(this::class.java)

	fun deltaOppdaterModerenheter() {
		deltaOppdaterEnhet(enhetType = EnhetType.MODERENHET) {
			val moderenhet = bronnoysundClient.hentModerenhet(it.organisasjonsnummer)

			if (moderenhet == null) { // GONE
				log.info("Moderenhet orgnr=${it.organisasjonsnummer} er fjernet fra brreg")

				return@deltaOppdaterEnhet EnhetService.UpsertEnhet(
					organisasjonsnummer = it.organisasjonsnummer,
					navn = "Fjernet virksomhet",
					overordnetEnhetOrgNr = null,
				)
			}

			if ( moderenhet.slettedato != null) {
				log.info("Moderenhet orgnr=${it.organisasjonsnummer} er slettet fra brreg")

				return@deltaOppdaterEnhet EnhetService.UpsertEnhet(
					organisasjonsnummer = it.organisasjonsnummer,
					navn = "${moderenhet.navn} (slettet)",
					overordnetEnhetOrgNr = null,
				)
			}

			EnhetService.UpsertEnhet(
				organisasjonsnummer = moderenhet.organisasjonsnummer,
				navn = moderenhet.navn,
				overordnetEnhetOrgNr = null,
			)
		}
	}

	fun deltaOppdaterUnderenheter() {
		deltaOppdaterEnhet(enhetType = EnhetType.UNDERENHET) {
			val underenhet = bronnoysundClient.hentUnderenhet(it.organisasjonsnummer)

			if (underenhet == null) { // GONE
				log.info("Underenhet orgnr=${it.organisasjonsnummer} er fjernet fra brreg")

				return@deltaOppdaterEnhet EnhetService.UpsertEnhet(
					organisasjonsnummer = it.organisasjonsnummer,
					navn = "Fjernet virksomhet",
					overordnetEnhetOrgNr = UKJENT_VIRKSOMHET_NR
				)
			}

			if (underenhet.slettedato != null) {
				log.info("Underenhet orgnr=${it.organisasjonsnummer} er slettet fra brreg")

				return@deltaOppdaterEnhet EnhetService.UpsertEnhet(
					organisasjonsnummer = it.organisasjonsnummer,
					navn = "${underenhet.navn} (slettet)",
					overordnetEnhetOrgNr = UKJENT_VIRKSOMHET_NR
				)
			}

			if (underenhet.overordnetEnhet == null) {
				log.warn("Underenhet orgnr=${underenhet.organisasjonsnummer} mangler overordnet enhet. oppdatering_id=${it.oppdateringId}")

				return@deltaOppdaterEnhet EnhetService.UpsertEnhet(
					organisasjonsnummer = it.organisasjonsnummer,
					navn = underenhet.navn,
					overordnetEnhetOrgNr = UKJENT_VIRKSOMHET_NR
				)
			}

			EnhetService.UpsertEnhet(
				organisasjonsnummer = underenhet.organisasjonsnummer,
				navn = underenhet.navn,
				overordnetEnhetOrgNr = underenhet.overordnetEnhet
			)
		}
	}

	private fun deltaOppdaterEnhet(enhetType: EnhetType, mapper: (oppdatering: EnhetOppdatering) -> EnhetService.UpsertEnhet?) {
		val progresjon = deltaOppdateringRepository.hentOppdateringProgresjon(enhetType)

		log.info("Starter delta oppdatering av $enhetType fra oppdatering_id=${progresjon.oppdateringId}")

		val oppdateringer =
			if (enhetType == EnhetType.MODERENHET)
				bronnoysundClient.hentModerenhetOppdateringer(progresjon.oppdateringId, OPPDATERINGER_SIZE)
			else
				bronnoysundClient.hentUnderenhetOppdateringer(progresjon.oppdateringId, OPPDATERINGER_SIZE)

		log.info("Antall oppdateringer: ${oppdateringer.size}. Har gjenstående oppdateringer: ${oppdateringer.size == OPPDATERINGER_SIZE}")

		if (oppdateringer.isEmpty()) {
			log.info("Ingen oppdateringer for $enhetType")
			return
		}

		val upserts = oppdateringer
			.mapNotNull { mapper.invoke(it) }

		enhetService.oppdaterEnheter(upserts)

		val sisteOppdateringId = oppdateringer.maxOf { it.oppdateringId }

		// Legger til +1 på oppdatering id slik at vi ikke henter den siste enheten i neste bolk
		deltaOppdateringRepository.oppdaterProgresjon(enhetType, sisteOppdateringId + 1)

		log.info("Fullførte delta oppdatering av $enhetType, sisteOppdateringId=$sisteOppdateringId")
	}

}


