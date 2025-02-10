package com.example.credentials_receiver

import OwnLocalReceiver
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.script.Script
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.slides.v1.Slides
import com.google.api.services.slides.v1.SlidesScopes
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.time.Duration.Companion.minutes

class GoogleApiServicesProvider(
    private var scope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { context, throwable ->
                } +
                CoroutineName("Google api callback scope")
    )
) {
    companion object {
        private var credential: Credential? = null
        private var googleServices: GoogleServices? = null
        private const val TOKEN_EXPIRATION_OFFSET = 300 // 300 seconds, 5 Min
        private const val TOKEN_FILE_NAME =
            "StoredCredential" // name from internals of google oauth2 lib. Can't be changed
        private const val temporaryFolder = "./tmp"
        private var environment = "127.0.0.1:8081"
        private const val clientSecrets =
            "eyJ3ZWIiOnsiY2xpZW50X2lkIjoiODY5MTc0NDQ5MzE5LWpxODU3aGFxcTVydjh2YTRxNG9hb2lrdXEzNnJrM3JzLmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwicHJvamVjdF9pZCI6Im1zLXJwcy1hZ2dyZWdhdG9yIiwiYXV0aF91cmkiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20vby9vYXV0aDIvYXV0aCIsInRva2VuX3VyaSI6Imh0dHBzOi8vb2F1dGgyLmdvb2dsZWFwaXMuY29tL3Rva2VuIiwiYXV0aF9wcm92aWRlcl94NTA5X2NlcnRfdXJsIjoiaHR0cHM6Ly93d3cuZ29vZ2xlYXBpcy5jb20vb2F1dGgyL3YxL2NlcnRzIiwiY2xpZW50X3NlY3JldCI6IkdPQ1NQWC1iaVVEcVRlNng0TDJoODNxd1R3ZnluaFpjUGE2In19"
    }

    private val docsScopes = listOf(SlidesScopes.PRESENTATIONS, DriveScopes.DRIVE, SheetsScopes.SPREADSHEETS)
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory: JacksonFactory = JacksonFactory.getDefaultInstance()

    fun getGoogleServices(): GoogleServices {

        credential = getCredentials()
        credential!!.let {
            googleServices = GoogleServices(
                drive = getDriveService(it),
                sheets = getSheetsService(it),
                slides = getSlidesService(it),
                script = getScriptsService(it)
            )
        }


        val originalFile = File("$temporaryFolder/$TOKEN_FILE_NAME")
        val txtfile = File("$temporaryFolder/$TOKEN_FILE_NAME.txt")
        txtfile.writeText(
            String(
                Base64.getEncoder().encode(
                    originalFile.readBytes()
                )
            )
        )

        return googleServices!!
    }

    private fun getCredentials(): Credential {
        val clientSecrets = GoogleClientSecrets.load(
            jsonFactory,
            String(Base64.getDecoder().decode(clientSecrets)).reader()
        )
        File("$temporaryFolder/$TOKEN_FILE_NAME").delete()

        scope.launch {
            googleCredentialsSubflow(clientSecrets)

            val file = File("$temporaryFolder/", TOKEN_FILE_NAME)
            val base64text = String(Base64.getEncoder().encode(file.readBytes()))
            print("Here's new token: $base64text")

        }

        return googleCredentialsSubflow(clientSecrets)
    }

    private fun googleCredentialsSubflow(clientSecrets: GoogleClientSecrets): Credential {
        environment = environment.replace("http://", "")
        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jsonFactory,
            clientSecrets,
            docsScopes
        )
            .setDataStoreFactory(FileDataStoreFactory(File(temporaryFolder)))
            .setAccessType("offline")
            .setApprovalPrompt("force")
            .build()
        val receiverBuilder = OwnLocalReceiver.Builder().setCallbackPath("/life-strategy/callback/google")
            .setHost(environment).setPort(8081)

        if (environment.contains("localhost")) {
            receiverBuilder.port = 8081
        }

        val receiver = receiverBuilder.build()

        return AuthorizationCodeInstalledApp(flow, receiver)
            .authorize("user")
    }

    private fun getDriveService(credential: Credential): Drive {
        return Drive.Builder(httpTransport, jsonFactory, setHttpTimeout(credential))
            .build()
    }

    private fun getSheetsService(credential: Credential): Sheets {
        return Sheets.Builder(httpTransport, jsonFactory, setHttpTimeout(credential))
            .build()
    }

    private fun getSlidesService(credential: Credential): Slides {
        return Slides.Builder(httpTransport, jsonFactory, setHttpTimeout(credential))
            .build()
    }

    private fun getScriptsService(credential: Credential): Script {
        return Script.Builder(httpTransport, jsonFactory, setHttpTimeout(credential))
            .build()
    }

    private fun setHttpTimeout(credential: Credential): HttpRequestInitializer = HttpRequestInitializer { request ->
        credential.initialize(request)
        request.connectTimeout = 3.minutes.inWholeMilliseconds.toInt()
        request.readTimeout = 3.minutes.inWholeMilliseconds.toInt()
    }
}
