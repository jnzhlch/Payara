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

/**
 * Resolves whether a configured realm classname is a (subclass of) FileRealm,
 * so admin-realm can switch to a hardening subclass without breaking sites
 * that compared against the literal FileRealm classname.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates.
 */
public final class RealmClassNames {

    public static final String FILE_REALM =
            "com.sun.enterprise.security.auth.realm.file.FileRealm";

    private RealmClassNames() { }

    public static boolean isFileRealm(String className) {
        return isAssignableFrom(FILE_REALM, className);
    }

    /** Package-private so tests can exercise the isAssignableFrom logic with
     *  class hierarchies visible inside common-util (FileRealm itself is in
     *  security/core and not on the common-util test classpath). */
    static boolean isAssignableFrom(String baseClassFqn, String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        try {
            ClassLoader cl = RealmClassNames.class.getClassLoader();
            Class<?> base = Class.forName(baseClassFqn, false, cl);
            Class<?> cfg = Class.forName(className, false, cl);
            return base.isAssignableFrom(cfg);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
