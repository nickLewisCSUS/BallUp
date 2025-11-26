package com.nicklewis.ballup.model

/**
 * Controls who can join a run.
 *
 * Stored in Firestore as the enum name string (OPEN / HOST_APPROVAL / INVITE_ONLY).
 */
enum class RunAccess {
    OPEN,          // Anyone can join immediately (today's behavior)
    HOST_APPROVAL, // Anyone can request; host must approve
    INVITE_ONLY    // Only invited users can join
}