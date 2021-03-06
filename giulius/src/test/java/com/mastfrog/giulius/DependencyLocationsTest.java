/*
 * The MIT License
 *
 * Copyright 2010-2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.giulius;

import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author tim
 */
@Defaults(namespace=@Namespace("hoo"), value="foo=whuz\nchortle=buzz")
public class DependencyLocationsTest {

    File dir = new File(new File(System.getProperty("java.io.tmpdir")), getClass().getName() + "_" + System.currentTimeMillis());
    File hooProps = new File(dir, "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
    File defProps = new File(dir, SettingsBuilder.DEFAULT_NAMESPACE + SettingsBuilder.DEFAULT_EXTENSION);
    File genDefProps = new File(dir, SettingsBuilder.GENERATED_PREFIX + "hoo" + SettingsBuilder.DEFAULT_EXTENSION);

    @Before
    public void setUp() throws IOException {
//        System.setProperty(SettingsBuilder.class.getName() + ".log", "true");
//        System.setProperty(Dependencies.class.getName() + ".log", "true");
//        System.setProperty(Settings.class.getName() + ".log", "true");

        dir.mkdirs();
        hooProps = new File(dir, "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
        defProps = new File(dir, SettingsBuilder.DEFAULT_NAMESPACE + SettingsBuilder.DEFAULT_EXTENSION);
        genDefProps = new File(dir, SettingsBuilder.GENERATED_PREFIX + "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
        assertTrue(hooProps.createNewFile());
        assertTrue(defProps.createNewFile());
        assertTrue(genDefProps.createNewFile());

        Properties fpp = new Properties();
        Properties dpp = new Properties();
        Properties gpp = new Properties();

        fpp.setProperty("hoo", "bar");
        dpp.setProperty("hoo", "ugg");
        gpp.setProperty("hoo", "goo");

        fpp.setProperty("stuff", "moo");
        dpp.setProperty("werg", "gweez");
        gpp.setProperty("mab", "pladge");

        store(fpp, hooProps);
        store(dpp, defProps);
        store(gpp, genDefProps);
    }

    @Test
    public void test() throws IOException {
        DependenciesBuilder b = new DependenciesBuilder();
        b.addDefaultLocation(dir);
        b.addNamespace("hoo");
        b.addDefaultSettings();
        Dependencies deps = b.build();

        Settings hooNs = deps.getSettings("hoo");
        assertNotNull(hooNs);

        Settings def = deps.getSettings(Namespace.DEFAULT);
        assertNotNull(def);

        assertEquals ("bar", hooNs.getString("hoo"));
        assertEquals ("moo", hooNs.getString("stuff"));

        assertEquals ("ugg", def.getString("hoo"));
        assertEquals ("buzz", hooNs.getString("chortle"));
    }

    private void store(Properties properties, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, "x");
        }
    }
}
