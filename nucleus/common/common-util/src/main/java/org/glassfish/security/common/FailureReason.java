/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 only,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/gpl-2.0.txt.
 */

package org.glassfish.security.common;

/**
 * Reason an authentication attempt failed, surfaced to subclass hooks so the
 * admin-realm hardening can count only genuine wrong-password failures.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public enum FailureReason {
    /** No such user in the keyfile. */
    USER_NOT_FOUND,
    /** User's password is in RESET state (no password set yet). */
    RESET_REQUIRED,
    /** Password hash did not match. */
    WRONG_PASSWORD,
    /** SSHA verification threw (bad encoding / algorithm). */
    SSHA_ERROR
}
