package com.rokidnav.phone.nav

import com.rokidnav.protocol.Maneuver

/**
 * Pure mapping from Navigation SDK types to protocol types. Keep it free of Android/SDK state so it
 * can be unit tested with hand-built inputs.
 *
 * TODO(M5):
 *  fun toManeuverUpdate(navInfo: NavInfo, seq: Long): ManeuverUpdate
 *      current = navInfo.currentStep -> Step(maneuver = mapManeuver(step.maneuver),
 *          roadName = step.fullRoadName, instruction = step.fullInstructionText,
 *          exitNumber = step.exitNumber, roundaboutExit = step.roundaboutTurnNumber,
 *          lanes = step.lanes?.map { ... })
 *      distanceToStepMeters = navInfo.distanceToCurrentStepMeters
 *      timeToStepSeconds   = navInfo.timeToCurrentStepSeconds
 *      next = navInfo.remainingSteps.firstOrNull()?.let(::toStep)
 *  fun toNavState(state: Int): NavState   // ENROUTE->ACTIVE, REROUTING->REROUTING, STOPPED->ENDED, UNKNOWN->ROUTING
 */
object NavInfoMapper {

    /** Maps StepInfo.MANEUVER_* constants. Fill in with the real constant names in M5. */
    fun mapManeuver(sdkManeuver: Int): Maneuver = when (sdkManeuver) {
        // StepInfo.MANEUVER_TURN_LEFT -> Maneuver.TURN_LEFT
        // StepInfo.MANEUVER_TURN_RIGHT -> Maneuver.TURN_RIGHT
        // ... one line per constant; unknown -> UNKNOWN (glasses show a generic arrow)
        else -> Maneuver.UNKNOWN
    }
}
