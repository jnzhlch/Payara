/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at legal/OPEN-SOURCE-LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient of the option to distribute your version of this file under
 * either the CDDL or the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
// Portions Copyright [2026] Payara Foundation and/or its affiliates

package org.glassfish.security.common;

import org.junit.Test;
import static org.junit.Assert.*;

class RcnBase {}
class RcnSub extends RcnBase {}
class RcnUnrelated {}

public class RealmClassNamesTest {

    @Test
    public void nullAndBlank() {
        assertFalse(RealmClassNames.isFileRealm(null));
        assertFalse(RealmClassNames.isFileRealm(""));
        assertFalse(RealmClassNames.isFileRealm("  "));
    }

    @Test
    public void assignableTrueForSubclass() {
        assertTrue(RealmClassNames.isAssignableFrom(
                RcnBase.class.getName(), RcnSub.class.getName()));
    }

    @Test
    public void assignableFalseForUnrelated() {
        assertFalse(RealmClassNames.isAssignableFrom(
                RcnBase.class.getName(), RcnUnrelated.class.getName()));
        assertFalse(RealmClassNames.isAssignableFrom(
                RcnBase.class.getName(), "java.lang.String"));
    }

    @Test
    public void missingClassReturnsFalse() {
        assertFalse(RealmClassNames.isAssignableFrom(
                "org.does.Not.Exist", RcnSub.class.getName()));
        assertFalse(RealmClassNames.isAssignableFrom(
                RcnBase.class.getName(), "org.does.Not.Exist"));
    }

    @Test
    public void isFileRealmInCommonUtilReturnsFalseDueToCNF() {
        // common-util test CP lacks security/core's FileRealm -> CNF -> false.
        // The real true-path is covered by security/core's RealmClassNamesFileRealmTest.
        assertFalse(RealmClassNames.isFileRealm(
                "com.sun.enterprise.security.auth.realm.file.FileRealm"));
    }
}
