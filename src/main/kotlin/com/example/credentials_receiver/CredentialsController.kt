package com.example.credentials_receiver

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@CrossOrigin("*")
@RestController
@RequestMapping("/receive")
class  CredentialsController {
    @GetMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receive() {
        GoogleApiServicesProvider().getGoogleServices()
    }
}