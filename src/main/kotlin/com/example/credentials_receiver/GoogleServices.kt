package com.example.credentials_receiver

import com.google.api.services.drive.Drive
import com.google.api.services.script.Script
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.slides.v1.Slides

data class GoogleServices(
  val drive: Drive,
  val sheets: Sheets,
  val slides: Slides,
  val script: Script
)
