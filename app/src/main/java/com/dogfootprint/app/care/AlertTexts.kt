package com.dogfootprint.app.care

import com.dogfootprint.app.R

/**
 * The banner says what to do; the verdict word stays in the body (3rd review B3). One line per kind and per
 * screen it is read on, so a parked driver is never told to "take a break in 15 minutes".
 */
fun alertMessageRes(kind: AlertKind, ctx: AlertContext): Int = when (kind) {
    AlertKind.HEAT -> when (ctx) {
        AlertContext.DRIVING -> R.string.alert_heat_driving
        AlertContext.PARKED -> R.string.alert_heat_parked
        AlertContext.PRE_EXIT -> R.string.alert_heat_pre_exit
        AlertContext.PET_ALONE -> R.string.alert_heat_pet_alone
    }
    AlertKind.HVAC_OFF -> R.string.alert_hvac_off
    AlertKind.WINDOW_OPEN -> R.string.alert_window
    AlertKind.DOOR_OPEN -> R.string.alert_door
    AlertKind.BELT -> if (ctx == AlertContext.DRIVING) R.string.alert_belt_driving else R.string.alert_belt_parked
    AlertKind.MEAL -> R.string.alert_meal_fmt
    AlertKind.BREAK_DUE -> R.string.alert_break_due
    AlertKind.LEAVE_PREP -> R.string.alert_leave_prep
}
