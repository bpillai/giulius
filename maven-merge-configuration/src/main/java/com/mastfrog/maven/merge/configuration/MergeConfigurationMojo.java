/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.maven.merge.configuration;

import com.google.common.base.Objects;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;

/**
 * A Maven plugin which, on good days, merges together all properties files on
 * the classpath whicha live in target/classes/META-INF/settings into a single
 * file in the classes output dir.
 * <p/>
 * Use this when you want to merge multiple JARs using the default namespace for
 * settings into one big JAR without files randomly clobbering each other.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "merge-configuration", threadSafe = false)
public class MergeConfigurationMojo extends AbstractMojo {

    // FOR ANYONE UNDER THE ILLUSION THAT WHAT WE DO IS COMPUTER SCIENCE:
    //
    // The following two unused fields are magical.  Remove them and you get
    // a plugin which contains no mojo.
    @Parameter(defaultValue = "${localRepository}")
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private java.util.List remoteRepositories;
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;
    @Parameter(property = "mainClass", defaultValue = "none")
    private String mainClass;
    @Parameter(property = "jarName", defaultValue = "none")
    private String jarName;
    @Parameter(property = "exclude", defaultValue = "")
    private String exclude = "";
    private static final Pattern PAT = Pattern.compile("META-INF\\/settings\\/[^\\/]*\\.properties");
    private static final Pattern SERVICES = Pattern.compile("META-INF\\/services\\/\\S[^\\/]*\\.*");
    private static final Pattern REGISTRATIONS = Pattern.compile("META-INF\\/.*?\\/.*\\.registrations$");
    @Parameter(property = "skipMavenMetadata", defaultValue = "true")
    private boolean skipMavenMetadata = true;
    @Parameter(property = "normalizeMetaInfPropertiesFiles", defaultValue = "true")
    private boolean normalizeMetaInfPropertiesFiles = true;
    @Parameter(property = "normalizeMetaInfPropertiesFiles", defaultValue = "false")
    private boolean skipLicenseFiles = false;

    private static final Pattern SIG1 = Pattern.compile("META-INF\\/[^\\/]*\\.SF");
    private static final Pattern SIG2 = Pattern.compile("META-INF\\/[^\\/]*\\.DSA");
    private static final Pattern SIG3 = Pattern.compile("META-INF\\/[^\\/]*\\.RSA");

    @Component
    private ProjectDependenciesResolver resolver;
    @Component
    MavenProject project;
    private static final boolean notSigFile(String name) {
        return !SIG1.matcher(name).find() && !SIG2.matcher(name).find() && !SIG3.matcher(name).find();
    }

    private final boolean shouldSkip(String name) {
        boolean result = "META-INF/MANIFEST.MF".equals(name) || "META-INF/".equals(name) || "META-INF/INDEX.LIST".equals(name)
                || (skipMavenMetadata && name.startsWith("META-INF/maven"));

        if (!result && skipLicenseFiles && name.startsWith("META-INF")) {
            result = name.toLowerCase().contains("license");
        }
        if (result) {
            getLog().warn("OMIT " + name);
        }
        return result;
    }
    private List<String> readLines(InputStream in) throws IOException {
        List<String> result = new LinkedList<>();
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                result.add(line);
            }
        }
        return result;
    }

    private static int copy(final InputStream in, final OutputStream out)
            throws IOException {
        final byte[] buffer = new byte[4096];
        int bytesCopied = 0;
        for (;;) {
            int byteCount = in.read(buffer, 0, buffer.length);
            if (byteCount <= 0) {
                break;
            } else {
                out.write(buffer, 0, byteCount);
                bytesCopied += byteCount;
            }
        }
        return bytesCopied;
    }

    private String strip(String name) {
        int ix = name.lastIndexOf(".");
        if (ix >= 0 && ix != name.length() - 1) {
            name = name.substring(ix + 1);
        }
        return name;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // XXX a LOT of duplicate code here
        Log log = super.getLog();
        log.info("Merging properties files");
        if (repoSession == null) {
            throw new MojoFailureException("RepositorySystemSession is null");
        }
        List<File> jars = new ArrayList<>();
        List<String> exclude = new LinkedList<>();
        for (String ex : this.exclude.split(",")) {
            ex = ex.trim();
            ex = ex.replace('.', '/');
            exclude.add(ex);
        }
        try {
            DependencyResolutionResult result
                    = resolver.resolve(new DefaultDependencyResolutionRequest(project, repoSession));
            log.info("FOUND " + result.getDependencies().size() + " dependencies");
            for (Dependency d : result.getDependencies()) {
                switch (d.getScope()) {
                    case "test":
                    case "provided":
                        break;
                    default:
                        File f = d.getArtifact().getFile();
                        if (f.getName().endsWith(".jar") && f.isFile() && f.canRead()) {
                            jars.add(f);
                        }
                }
            }
        } catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Collecting dependencies failed", ex);
        }

        Map<String, Properties> m = new LinkedHashMap<>();

        Map<String, Set<String>> linesForName = new LinkedHashMap<>();
        Map<String, Integer> fileCountForName = new HashMap<>();

        boolean buildMergedJar = true;//mainClass != null && !"none".equals(mainClass);
        JarOutputStream jarOut = null;
        Set<String> seen = new HashSet<>();

        Map<String, List<String>> originsOf = new HashMap<>();
        try {
            if (buildMergedJar) {
                try {
                    File outDir = new File(project.getBuild().getOutputDirectory()).getParentFile();
                    File jar = new File(outDir, project.getBuild().getFinalName() + ".jar");
                    if (!jar.exists()) {
                        throw new MojoExecutionException("Could not find jar " + jar);
                    }
                    try (JarFile jf = new JarFile(jar)) {
                        Manifest manifest = new Manifest(jf.getManifest());
                        if (mainClass != null) {
                            manifest.getMainAttributes().putValue("Main-Class", mainClass);
                        }
                        String jn = jarName == null || "none".equals(jarName) ? mainClass == null ? "merged-jar" : strip(mainClass) : jarName;
                        File outJar = new File(outDir, jn + ".jar");
                        log.info("Will build merged JAR " + outJar);
                        if (outJar.equals(jar)) {
                            throw new MojoExecutionException("Merged jar and output jar are the same file: " + outJar);
                        }
                        if (!outJar.exists()) {
                            outJar.createNewFile();
                        }
                        jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outJar)), manifest);
                        jarOut.setLevel(9);
                        jarOut.setComment("Merged jar created by " + getClass().getName());
                        Enumeration<JarEntry> en = jf.entries();
                        while (en.hasMoreElements()) {
                            JarEntry e = en.nextElement();
                            String name = e.getName();
                            List<String> origins = originsOf.get(name);
                            if (origins == null) {
                                origins = new ArrayList<>(5);
                                originsOf.put(name, origins);
                            }
                            origins.add(jar.getName());
                            for (String s : exclude) {
                                if (!s.isEmpty() && name.startsWith(s)) {
                                    log.info("EXCLUDE " + s + " " + exclude);
                                    continue;
                                }
                            }
//                            if (!seen.contains(name)) {
                            switch (name) {
                                case "META-INF/MANIFEST.MF":
                                case ".netbeans_automatic_build":
                                case "META-INF/INDEX.LIST":
                                case "META-INF/":
                                    break;
                                default:
                                    if ("META-INF/LICENSE".equals(name)
                                            || "META-INF/LICENSE.txt".equals(name)
                                            || "META-INF/license".equals(name)
                                            || "META-INF/NOTICE".equals(name)
                                            || "META-INF/notice".equals(name)
                                            || "META-INF/license.txt".equals(name)
                                            || "META-INF/http/pages.list".equals(name)
                                            || "META-INF/http/modules.list".equals(name)
                                            || "META-INF/http/numble.list".equals(name)
                                            || "META-INF/settings/namespaces.list".equals(name)
                                            || (name.startsWith("META-INF") && name.endsWith(".registrations"))) {
                                        if (shouldSkip(name)) {
                                            break;
                                        }
                                        Set<String> s = linesForName.get(name);
                                        if (s == null) {
                                            s = new LinkedHashSet<>();
                                            linesForName.put(name, s);
                                        }
                                        Integer ct = fileCountForName.get(name);
                                        if (ct == null) {
                                            ct = 1;
                                        }
                                        fileCountForName.put(name, ct);
                                        try (InputStream in = jf.getInputStream(e)) {
                                            s.addAll(readLines(in));
                                        }
                                        break;
                                    }

                                    if (name.startsWith("META-INF/services/") && !name.endsWith("/")) {
                                        Set<String> s2 = linesForName.get(name);
                                        if (s2 == null) {
                                            s2 = new HashSet<>();
                                            linesForName.put(name, s2);
                                        }
                                        Integer ct2 = fileCountForName.get(name);
                                        if (ct2 == null) {
                                            ct2 = 1;
                                        }
                                        fileCountForName.put(name, ct2);
                                        try (InputStream in = jf.getInputStream(e)) {
                                            s2.addAll(readLines(in));
                                        }
                                        seen.add(name);
                                    } else if (PAT.matcher(name).matches()) {
                                        log.info("Include " + name);
                                        Properties p = new Properties();
                                        try (InputStream in = jf.getInputStream(e)) {
                                            p.load(in);
                                        }
                                        Properties all = m.get(name);
                                        if (all == null) {
                                            all = p;
                                            m.put(name, p);
                                        } else {
                                            for (String key : p.stringPropertyNames()) {
                                                if (all.containsKey(key)) {
                                                    Object old = all.get(key);
                                                    Object nue = p.get(key);
                                                    if (!Objects.equal(old, nue)) {
                                                        log.warn(key + '=' + nue + " in " + jar + '!' + name + " overrides " + key + '=' + old);
                                                    }
                                                }
                                            }
                                            all.putAll(p);
                                        }
                                    } else if (!seen.contains(name) && notSigFile(name)) {
                                        log.info("Bundle " + name);
                                        JarEntry je = new JarEntry(name);
                                        je.setTime(e.getTime());
                                        try {
                                            jarOut.putNextEntry(je);
                                        } catch (ZipException ex) {
                                            throw new MojoExecutionException("Exception putting zip entry " + name, ex);
                                        }
                                        try (InputStream in = jf.getInputStream(e)) {
                                            copy(in, jarOut);
                                        }
                                        jarOut.closeEntry();
                                        seen.add(name);
                                    } else {
                                        log.warn("Skip " + name);
                                    }
                            }
//                            }
                            seen.add(e.getName());
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to create merged jar", ex);
                }
            }

            for (File f : jars) {
                log.info("Merge JAR " + f);
                try (JarFile jar = new JarFile(f)) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        JarEntry entry = en.nextElement();
                        String name = entry.getName();
                        List<String> origins = originsOf.get(name);
                        if (origins == null) {
                            origins = new ArrayList<>(5);
                            originsOf.put(name, origins);
                        }
                        origins.add(f.getName());
                        for (String s : exclude) {
                            if (!s.isEmpty() && name.startsWith(s)) {
                                log.info("EXCLUDE " + name + " " + exclude);
                                continue;
                            }
                        }
                        if (PAT.matcher(name).matches()) {
                            log.info("Include " + name + " in " + f);
                            Properties p = new Properties();
                            try (InputStream in = jar.getInputStream(entry)) {
                                p.load(in);
                            }
                            Properties all = m.get(name);
                            if (all == null) {
                                all = p;
                                m.put(name, p);
                            } else {
                                for (String key : p.stringPropertyNames()) {
                                    if (all.containsKey(key)) {
                                        Object old = all.get(key);
                                        Object nue = p.get(key);
                                        if (!Objects.equal(old, nue)) {
                                            log.warn(key + '=' + nue + " in " + f + '!' + name + " overrides " + key + '=' + old);
                                        }
                                    }
                                }
                                all.putAll(p);
                            }
                        } else if (REGISTRATIONS.matcher(name).matches() || SERVICES.matcher(name).matches() || "META-INF/settings/namespaces.list".equals(name) || "META-INF/http/pages.list".equals(name) || "META-INF/http/modules.list".equals(name) || "META-INF/http/numble.list".equals(name)) {
                            log.info("Include " + name + " in " + f);
                            try (InputStream in = jar.getInputStream(entry)) {
                                List<String> lines = readLines(in);
                                Set<String> all = linesForName.get(name);
                                if (all == null) {
                                    all = new LinkedHashSet<>();
                                    linesForName.put(name, all);
                                }
                                all.addAll(lines);
                            }
                            Integer ct = fileCountForName.get(name);
                            if (ct == null) {
                                ct = 1;
                            } else {
                                ct++;
                            }
                            fileCountForName.put(name, ct);
                        } else if (jarOut != null) {
//                            if (!seen.contains(name)) {
                            switch (name) {
                                case "META-INF/MANIFEST.MF":
                                case "META-INF/":
                                    break;
                                default:
                                    if ("META-INF/LICENSE".equals(name)
                                            || "META-INF/LICENSE.txt".equals(name)
                                            || "META-INF/license".equals(name)
                                            || "META-INF/NOTICE".equals(name)
                                            || "META-INF/notice".equals(name)
                                            || "META-INF/license.txt".equals(name)
                                            || "META-INF/http/pages.list".equals(name)
                                            || "META-INF/http/modules.list".equals(name)
                                            || "META-INF/http/numble.list".equals(name)
                                            || "META-INF/settings/namespaces.list".equals(name)
                                            || (name.startsWith("META-INF") && name.endsWith(".registrations"))) {
                                        if (shouldSkip(name)) {
                                            break;
                                        }
                                        Set<String> s = linesForName.get(name);
                                        if (s == null) {
                                            s = new LinkedHashSet<>();
                                            linesForName.put(name, s);
                                        }
                                        Integer ct = fileCountForName.get(name);
                                        if (ct == null) {
                                            ct = 1;
                                        }
                                        fileCountForName.put(name, ct);
                                        try (InputStream in = jar.getInputStream(entry)) {
                                            s.addAll(readLines(in));
                                        }
                                        break;
                                    }
                                    if (!seen.contains(name)) {
                                        if (!SIG1.matcher(name).find() && !SIG2.matcher(name).find() && !SIG3.matcher(name).find()) {
                                            JarEntry je = new JarEntry(name);
                                            je.setTime(entry.getTime());
                                            try {
                                                jarOut.putNextEntry(je);
                                            } catch (ZipException ex) {
                                                throw new MojoExecutionException("Exception putting zip entry " + name, ex);
                                            }
                                            try (InputStream in = jar.getInputStream(entry)) {
                                                copy(in, jarOut);
                                            }
                                            jarOut.closeEntry();
                                        }
                                    } else {
                                        if (!name.endsWith("/") && !name.startsWith("META-INF")) {
                                            log.warn("Saw more than one " + name + ".  One will clobber the other.");
                                        }
                                    }
                            }
//                            } else {
//                                if (!name.endsWith("/") && !name.startsWith("META-INF")) {
//                                    log.warn("Saw more than one " + name + ".  One will clobber the other.");
//                                }
//                            }
                            seen.add(name);
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error opening " + f, ex);
                }
            }
            if (!m.isEmpty()) {
                log.warn("Writing merged files: " + m.keySet());
            } else {
                return;
            }
            String outDir = project.getBuild().getOutputDirectory();
            File dir = new File(outDir);
            // Don't bother rewriting META-INF/services files of which there is
            // only one
//            for (Map.Entry<String, Integer> e : fileCountForName.entrySet()) {
//                if (e.getValue() == 1) {
//                    linesForName.remove(e.getKey());
//                }
//            }
            for (Map.Entry<String, Set<String>> e : linesForName.entrySet()) {
                if (shouldSkip(e.getKey())) {
                    continue;
                }
                File outFile = new File(dir, e.getKey());
                if (originsOf.get(e.getKey()).size() > 1) {
                    log.info("Combining " + outFile + " from " + sortedToString(originsOf.get(e.getKey())));
                } else {
                    log.debug("Rewriting " + outFile + " from " + sortedToString(originsOf.get(e.getKey())) + " for repeatable builds");
                }
                Set<String> lines = e.getValue();
                if (!outFile.exists()) {
                    try {
                        Path path = outFile.toPath();
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        path = Files.createFile(path);
                        outFile = path.toFile();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Could not create " + outFile, ex);
                    }
                }
                if (!outFile.isDirectory()) {
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        printLines(lines, out, true);
//                        try (PrintStream ps = new PrintStream(out)) {
//                            for (String line : lines) {
//                                ps.println(line);
//                            }
//                        }
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing " + outFile, ex);
                    }
                }
                if (jarOut != null) {
                    int count = fileCountForName.get(e.getKey());
                    if (count > 1) {
                        log.warn("Concatenating " + count + " copies of " + e.getKey());
                    }
                    JarEntry je = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(je);
                        printLines(lines, jarOut, false);
//                        PrintStream ps = new PrintStream(jarOut);
//                        for (String line : lines) {
//                            ps.println(line);
//                        }
                        jarOut.closeEntry();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing " + outFile, ex);
                    }
                }
            }
            for (Map.Entry<String, Properties> e : m.entrySet()) {
                if (shouldSkip(e.getKey())) {
                    continue;
                }
                File outFile = new File(dir, e.getKey());
                Properties local = new Properties();
                if (outFile.exists()) {
                    try {
                        try (InputStream in = new FileInputStream(outFile)) {
                            local.load(in);
                        }
                    } catch (IOException ioe) {
                        throw new MojoExecutionException("Could not read " + outFile, ioe);
                    }
                } else {
                    try {
                        Path path = outFile.toPath();
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        path = Files.createFile(path);
                        outFile = path.toFile();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Could not create " + outFile, ex);
                    }
                }
                Properties merged = e.getValue();
                for (String key : local.stringPropertyNames()) {
                    if (merged.containsKey(key) && !Objects.equal(local.get(key), merged.get(key))) {
                        log.warn("Overriding key=" + merged.get(key) + " with locally defined key=" + local.get(key));
                    }
                }
                merged.putAll(local);
                List<String> origins = originsOf.get(e.getKey());
                if (origins == null) {
                    throw new IllegalStateException("Don't have an origin for " + e.getKey() + " in " + originsOf);
                }
                String ogs = sortedToString(origins);
                log.info("Saving merged properties to " + outFile + " from " + originsOf.get(e.getKey()));
                String comment = "Merged by " + getClass().getSimpleName() + " from  " + ogs;
                try {
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        savePropertiesFile(merged, out, comment, true);
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to write " + outFile, ex);
                }
                if (jarOut != null) {
                    JarEntry props = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(props);
//                        merged.store(jarOut, comment);
                        savePropertiesFile(merged, jarOut, comment, false);
                    } catch (IOException ex) {
                        throw new MojoExecutionException("Failed to write jar entry " + e.getKey(), ex);
                    } finally {
                        try {
                            jarOut.closeEntry();
                        } catch (IOException ex) {
                            throw new MojoExecutionException("Failed to close jar entry " + e.getKey(), ex);
                        }
                    }
                }
                File copyTo = new File(dir.getParentFile(), "settings");
                if (!copyTo.exists()) {
                    copyTo.mkdirs();
                }
                File toFile = new File(copyTo, outFile.getName());
                try {
                    Files.copy(outFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to copy " + outFile + " to " + toFile, ex);
                }
            }
        } finally {
            if (jarOut != null) {
                try {
                    jarOut.close();
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to close Jar", ex);
                }
            }
        }
    }

    private static String sortedToString(List<String> all) {
        Collections.sort(all);
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = all.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private static final void savePropertiesFile(Properties props, OutputStream out, String comment, boolean close) throws IOException {
        // Stores properties file without date comments, with consistent key ordering and line terminators, for
        // repeatable builds
        List<String> keys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(keys);
        List<String> lines = new ArrayList<>();
        if (comment != null) {
            lines.add("# " + comment);
        }
        for (String key : keys) {
            String val = props.getProperty(key);
            key = convert(key, true);
            /* No need to escape embedded and trailing spaces for value, hence
                 * pass false to flag.
             */
            val = convert(val, false);
            lines.add(key + "=" + val);

        }
        printLines(lines, out, ISO_8859_1, close);
    }

    private static String convert(String keyVal, boolean escapeSpace) {
        int len = keyVal.length();
        StringBuilder sb = new StringBuilder(len * 2 < 0 ? Integer.MAX_VALUE : len * 2);

        for (int i = 0; i < len; i++) {
            char ch = keyVal.charAt(i);
            if ((ch > 61) && (ch < 127)) {
                if (ch == '\\') {
                    sb.append("\\\\");
                } else {
                    sb.append(ch);
                }
                continue;
            }
            switch (ch) {
                case ' ':
                    sb.append(escapeSpace ? ESCAPED_SPACE : ' ');
                    break;
                case '\n':
                    appendEscaped('n', sb);
                    break;
                case '\r':
                    appendEscaped('r', sb);
                    break;
                case '\t':
                    appendEscaped('t', sb);
                    break;
                case '\f':
                    appendEscaped('f', sb);
                    break;
                case '#':
                case '=':
                case '!':
                case ':':
                    sb.append('\\').append(ch);
                    sb.append(ch);
                    break;
                default:
                    if (((ch < 0x0020) || (ch > 0x007e))) {
                        appendEscapedHex(ch, sb);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static void appendEscaped(char c, StringBuilder sb) {
        sb.append('\\').append(c);
    }

    private static void appendEscapedHex(char c, StringBuilder sb) {
        sb.append('\\').append('u');
        for (int i : NIBBLES) {
            char hex = HEX[(c >> i) & 0xF];
            sb.append(hex);
        }
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Charset ISO_8859_1 = Charset.forName("8859_1");
    private static final char[] ESCAPED_SPACE = "\\ ".toCharArray();
    private static final int[] NIBBLES = new int[]{12, 8, 4, 0};
    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static int printLines(Iterable<String> lines, OutputStream out, boolean close) throws IOException {
        return printLines(lines, out, UTF_8, close);
    }

    private static int printLines(Iterable<String> lines, OutputStream out, Charset encoding, boolean close) throws IOException {
        // Ensures UTF-8 encoding and avoids non-repeatable builds due to Windows line endings
        int count = 0;
        for (String line : lines) {
            byte[] bytes = line.getBytes(encoding);
            out.write(bytes);
            out.write('\n');
            count++;
        }
        if (close) {
            out.close();
        }
        return count;
    }
}
