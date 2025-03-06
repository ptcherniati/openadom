package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.*;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.*;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.services.RelationalService;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("integration.rest")
@Slf4j
public class RightsTest {
    @Autowired
    private Fixtures fixtures;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private UUID authUserId;
    private Cookie authCookie;
    @Autowired
    private AuthHelper authHelper;

    @BeforeEach
    public void createUser() throws Exception {
        CreateUserResult authUser;
        try {
            final OreSiUser user = authenticationService.getByIdOrLogin("poussin");
            authUser = CreateUserResult.of(user);
        } catch (final Exception e) {
            authUser = createUserIfNotExists("poussin", "xxxxxxxx", "poussin@inrae.fr");
        }
        authUserId = authUser.userId();
        setToActive(authUserId);
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        addRoleAdmin(authUser);
    }

    private CreateUserResult createUserIfNotExists(String login, String password, String mail) throws Exception {
        if (mockMvc.perform(post("/api/v1/login")
                        .param("login", login)
                        .param("password", password))
                    .andReturn()
                    .getResponse().getStatus() > 300) {
            return authenticationService.createUser(login, password, mail);
        } else {
            OreSiUser userByLogin = userRepository.findByLogin(login).orElse(null);
            return CreateUserResult.of(Objects.requireNonNull(userByLogin));
        }

    }

    @Transactional
    void setToActive(final UUID userId) {
        namedParameterJdbcTemplate.update(
                """
                        UPDATE public.oresiuser SET accountstate = 'active' WHERE id = :id
                        """, Map.of("id", userId));
    }

    @Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        namedParameterJdbcTemplate.update("grant \"openAdomAdmin\" to \"" + dbUserResult.userId().toString() + "\" WITH INHERIT TRUE;", Map.of());
    }

    @Test
    public void noCookieTest() throws Exception {
        Exception resolvedException = mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResolvedException();
        Assertions.assertEquals(DisconnectedException.class, resolvedException.getClass());
    }

    @Test
    public void timeOutCookie() throws Exception {
        OreSiUser oreSiUser = new OreSiUser();
        oreSiUser.setId(authUserId);
        OreSiUserRequestClient oreSiUserRequestClient = new OreSiUserRequestClient(authUserId, OreSiUserRole.forUser(oreSiUser));
        Cookie cookie = newCookie(oreSiUserRequestClient);
        Exception resolvedException = mockMvc.perform(get("/api/v1/applications")
                        .cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResolvedException();
        Assertions.assertEquals(DisconnectedException.class, resolvedException.getClass());
    }

    private Cookie newCookie(final OreSiUserRequestClient requestClient) {
        final String json;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            final JwtCookieValue jwtCookieValue = new JwtCookieValue(requestClient);
            json = objectMapper.writeValueAsString(jwtCookieValue);
        } catch (final JsonProcessingException e) {
            throw new SiOreIllegalArgumentException(
                    "requestMapperSerializationError",
                    Map.of(
                            "requestClient", requestClient,
                            "objectMapper", objectMapper,
                            "message", e.getLocalizedMessage()
                    )
            );
            //throw new OreSiTechnicalException("impossible de sérialiser " + requestClient + " avec " + objectMapper, e);
        }
        final Date issuedAt = new Date();
        final String token = authHelper.buildToken(json,issuedAt,0);
        final Cookie cookie = new Cookie(AuthHelper.JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }
}
