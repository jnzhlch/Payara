/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 */
package com.sun.enterprise.security.auth.realm.file;

import java.io.File;
import java.io.IOException;

import org.glassfish.security.common.FileRealmStorageManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class FileRealmFactoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void createStorageManagerReturnsInstanceByDefault() throws Exception {
        File kf = tmp.newFile("kf");
        FileRealm realm = new FileRealm();
        FileRealmStorageManager sm = realm.createStorageManager(kf.getAbsolutePath());
        assertNotNull(sm);
        assertSame(FileRealmStorageManager.class, sm.getClass());
    }

    @Test
    public void subclassCanOverrideFactory() throws Exception {
        File kf = tmp.newFile("kf");
        FileRealm sub = new FileRealm() {
            @Override
            protected FileRealmStorageManager createStorageManager(String file) throws IOException {
                return new FileRealmStorageManager(file) { }; // anonymous subclass proves override reached
            }
        };
        FileRealmStorageManager sm = sub.createStorageManager(kf.getAbsolutePath());
        assertNotSame(FileRealmStorageManager.class, sm.getClass());
    }
}
