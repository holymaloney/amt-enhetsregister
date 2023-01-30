package no.nav.amt_enhetsregister.repository

import no.nav.amt_enhetsregister.repository.type.EnhetType
import no.nav.amt_enhetsregister.test_utils.DatabaseTestUtils
import no.nav.amt_enhetsregister.test_utils.SingletonPostgresContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class DeltaOppdateringProgresjonRepositoryTest {

	private val dataSource = SingletonPostgresContainer.getDataSource()

	lateinit var jdbcTemplate: JdbcTemplate

	lateinit var deltaOppdateringProgresjonRepository: DeltaOppdateringProgresjonRepository

	@BeforeEach
	fun migrate() {
		jdbcTemplate = JdbcTemplate(dataSource)
		deltaOppdateringProgresjonRepository = DeltaOppdateringProgresjonRepository(jdbcTemplate)
		DatabaseTestUtils.cleanAndInitDatabase(dataSource,"/db/delta-enhet-oppdatering.sql")
	}

	@Test
	fun `hentOppdateringProgresjon skal hente oppdatering progresjon for moderenhet`() {
		val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.MODERENHET)

		assertEquals(1234, progresjon.oppdateringId)
		assertEquals(EnhetType.MODERENHET, progresjon.enhetType)
	}

	@Test
	fun `hentOppdateringProgresjon skal hente oppdatering progresjon for underenhet`() {
		val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.UNDERENHET)

		assertEquals(5678, progresjon.oppdateringId)
		assertEquals(EnhetType.UNDERENHET, progresjon.enhetType)
	}

	@Test
	fun `oppdaterProgresjon skal oppdatere oppdateringsid`() {
		deltaOppdateringProgresjonRepository.oppdaterProgresjon(EnhetType.MODERENHET, 5)

		val progresjon = deltaOppdateringProgresjonRepository.hentOppdateringProgresjon(EnhetType.MODERENHET)

		assertEquals(5, progresjon.oppdateringId)
	}

}
