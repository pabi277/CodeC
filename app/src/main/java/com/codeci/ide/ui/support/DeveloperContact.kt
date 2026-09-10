package com.codeci.ide.ui.support

/**
 * Phase 41, round 2 (owner, 2026-09-10, verbatim): *"I want to sit as
 * developer not some other guy. So i want my number hard coded. Any
 * feedback comes to me no need for the user to set number the user know me
 * or don't know me does not matter a bit. So remove the boxes and set it
 * in the code."*
 *
 * So this is the ONE place the feedback identity lives: hardcoded, not a
 * setting, not editable in the app, not a DataStore key. Every feedback
 * channel (WhatsApp CHAT, EMAIL, the GitHub issue link's repo) points at
 * the developer. There are deliberately NO input fields for this anywhere
 * in the UI — `ExitSurveyTest` pins that no store key and no reply-to
 * field can come back.
 */
object DeveloperContact {

    /** The developer's WhatsApp, E.164 digits (wa.me wants no +, spaces or dashes). */
    const val WHATSAPP_E164 = "916296746606"

    /** The same number as humans read it (+91 62967 46606) — toasts and labels. */
    const val WHATSAPP_DISPLAY = "+91 62967 46606"

    /** The developer's reply-to email (the EMAIL fallback button). */
    const val EMAIL = "chakraborttypabi2772006@gmail.com"
}
