package no.nav.amt_enhetsregister.controller

import no.nav.amt_enhetsregister.repository.type.Enhet
import no.nav.amt_enhetsregister.service.EnhetService
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@EnableJwtTokenValidation
@ActiveProfiles("test")
@WebMvcTest(controllers = [EnhetController::class])
class EnhetControllerTest {

	companion object {
		private val server = MockOAuth2Server()

		init {
			server.start()
			System.setProperty("MOCK_AZURE_AD_DISCOVERY_URL", server.wellKnownUrl("azuread").toString())
		}

		@AfterAll
		@JvmStatic
		fun cleanup() {
			server.shutdown()
		}
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockBean
	private lateinit var enhetService: EnhetService

	@Test
	fun `hentEnhet should return 401 when not authenticated`() {
		val response = mockMvc.perform(
			MockMvcRequestBuilders.get("/api/enhet")
				.queryParam("organisasjonsnummer", "test")
		).andReturn().response

		assertEquals(401, response.status)
	}

	@Test
	fun `hentEnhet should return 200 when enhet exists`() {
		`when`(enhetService.hentEnhet(anyString())).thenReturn(Enhet(
			id = 42,
			organisasjonsnummer = "1234",
			navn = "test",
			overordnetEnhet = null
		))

		val token = server.issueToken("azuread", "test", "test").serialize()

		val response = mockMvc.perform(
			MockMvcRequestBuilders.get("/api/enhet")
				.queryParam("organisasjonsnummer", "1234")
				.header("Authorization", "Bearer $token")
		).andReturn().response

		verify(enhetService, times(1)).hentEnhet("1234")

		assertEquals(200, response.status)
	}

	@Test
	fun `hentEnhet should return 404 when enhet does not exist`() {
		`when`(enhetService.hentEnhet(anyString())).thenReturn(null)

		val token = server.issueToken("azuread", "test", "test").serialize()

		val response = mockMvc.perform(
			MockMvcRequestBuilders.get("/api/enhet")
				.queryParam("organisasjonsnummer", "test")
				.header("Authorization", "Bearer $token")
		).andReturn().response

		assertEquals(404, response.status)
	}

}
