/**
 * OSHI (https://github.com/oshi/oshi)
 *
 * Copyright (c) 2010 - 2019 The OSHI Project Team:
 * https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.software.os.linux;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.software.common.AbstractOSVersionInfoEx;
import oshi.software.os.OperatingSystem;
import oshi.util.ExecutingCommand;
import oshi.util.FileUtil;
import oshi.util.ParseUtil;

/**
 * <p>
 * LinuxOSVersionInfoEx class.
 * </p>
 * 
 * @deprecated Use {@link OperatingSystem.OSVersionInfo}
 */
@Deprecated
public class LinuxOSVersionInfoEx extends AbstractOSVersionInfoEx {

    private static final Logger LOG = LoggerFactory.getLogger(LinuxOSVersionInfoEx.class);

    /**
     * <p>
     * Constructor for LinuxOSVersionInfoEx.
     * </p>
     */
    public LinuxOSVersionInfoEx() {
        this(null, null);
    }

    /**
     * <p>
     * Constructor for LinuxOSVersionInfoEx.
     * </p>
     *
     * @param versionId
     *            a {@link java.lang.String} object.
     * @param codeName
     *            a {@link java.lang.String} object.
     */
    protected LinuxOSVersionInfoEx(String versionId, String codeName) {
        setVersion(versionId);
        setCodeName(codeName);
        if (getVersion() == null) {
            setVersionFromReleaseFiles();
        }
        if (getCodeName() == null) {
            setCodeName("");
        }
        List<String> procVersion = null;
        procVersion = FileUtil.readFile("/proc/version");
        if (!procVersion.isEmpty()) {
            String[] split = ParseUtil.whitespaces.split(procVersion.get(0));
            for (String s : split) {
                if (!"Linux".equals(s) && !"version".equals(s)) {
                    setBuildNumber(s);
                    return;
                }
            }
        }
        setBuildNumber("");
    }

    /*
     * Code below this point is largely a copy of LinuxOperatingSystem class except
     * family is not set. If this class has been called from that class then no new
     * information would be added here. This is provided in the odd event someone
     * wants to instantiate this class without having first gone through the
     * LinuxOperatingSystem
     */

    private void setVersionFromReleaseFiles() {
        // There are two competing options for family/version information.
        // Newer systems are adopting a standard /etc/os-release file:
        // https://www.freedesktop.org/software/systemd/man/os-release.html
        //
        // Some systems are still using the lsb standard which parses a
        // variety of /etc/*-release files and is most easily accessed via
        // the commandline lsb_release -a, see here:
        // http://linux.die.net/man/1/lsb_release
        // In this case, the /etc/lsb-release file (if it exists) has
        // optional overrides to the information in the /etc/distrib-release
        // files, which show: "Distributor release x.x (Codename)"
        //

        // Attempt to read /etc/system-release which has more details than
        // os-release on (CentOS and Fedora)
        if (readDistribRelease("/etc/system-release")) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return;
        }

        // Attempt to read /etc/os-release file.
        if (readOsRelease()) {
            // If successful, we're done. The version and possibly codeName
            // are set
            return;
        }

        // Attempt to execute the `lsb_release` command
        if (execLsbRelease()) {
            // If successful, we're done. The version and possibly codeName
            // are set
            return;
        }

        // The above two options should hopefully work on most
        // distributions. If not, we keep having fun.
        // Attempt to read /etc/lsb-release file
        if (readLsbRelease()) {
            // If successful, we're done. The version and possibly codeName
            // are set
            return;
        }

        // If we're still looking, we search for any /etc/*-release (or
        // similar) filename, for which the first line should be of the
        // "Distributor release x.x (Codename)" format or possibly a
        // "Distributor VERSION x.x (Codename)" format
        String etcDistribRelease = LinuxOperatingSystem.getReleaseFilename();
        if (readDistribRelease(etcDistribRelease)) {
            // If successful, we're done. The version and possibly codeName
            // are set
            return;
        }
        // Finally, if all else fails
        if (getVersion() == null) {
            setVersion(System.getProperty("os.version"));
        }
    }

    /**
     * Attempts to read /etc/os-release
     *
     * @return true if file successfully read and NAME= found
     */
    private boolean readOsRelease() {
        if (new File("/etc/os-release").exists()) {
            List<String> osRelease = FileUtil.readFile("/etc/os-release");
            // Search for NAME=
            for (String line : osRelease) {
                if (line.startsWith("VERSION=")) {
                    LOG.debug("os-release: {}", line);
                    // remove beginning and ending '"' characters, etc from
                    // VERSION="14.04.4 LTS, Trusty Tahr" (Ubuntu style)
                    // or VERSION="17 (Beefy Miracle)" (os-release doc style
                    line = ParseUtil.getDoubleQuoteStringValue(line).trim();
                    String[] split = line.split("[()]");
                    if (split.length <= 1) {
                        // If no parentheses, check for Ubuntu's comma format
                        split = line.split(", ");
                    }
                    if (split.length > 0) {
                        this.version = split[0].trim();
                    }
                    if (split.length > 1) {
                        this.codeName = split[1].trim();
                    }
                } else if (line.startsWith("VERSION_ID=") && this.version == null) {
                    LOG.debug("os-release: {}", line);
                    // remove beginning and ending '"' characters, etc from
                    // VERSION_ID="14.04"
                    this.version = ParseUtil.getDoubleQuoteStringValue(line).trim();
                }
            }
        }
        return this.version != null;
    }

    /**
     * Attempts to execute `lsb_release -a`
     *
     * @return true if the command successfully executed and Release: or
     *         Description: found
     */
    private boolean execLsbRelease() {
        // If description is of the format Distrib release x.x (Codename)
        // that is primary, otherwise use Distributor ID: which returns the
        // distribution concatenated, e.g., RedHat instead of Red Hat
        for (String line : ExecutingCommand.runNative("lsb_release -a")) {
            if (line.startsWith("Description:")) {
                LOG.debug("lsb_release -a: {}", line);
                line = line.replace("Description:", "").trim();
                if (line.contains(" release ")) {
                    this.version = parseRelease(line, " release ");
                }
            } else if (line.startsWith("Release:") && this.version == null) {
                LOG.debug("lsb_release -a: {}", line);
                this.version = line.replace("Release:", "").trim();
            } else if (line.startsWith("Codename:") && this.codeName == null) {
                LOG.debug("lsb_release -a: {}", line);
                this.codeName = line.replace("Codename:", "").trim();
            }
        }
        return this.version != null;
    }

    /**
     * Attempts to read /etc/lsb-release
     *
     * @return true if file successfully read and DISTRIB_RELEASE or
     *         DISTRIB_DESCRIPTION found
     */
    private boolean readLsbRelease() {
        if (new File("/etc/lsb-release").exists()) {
            List<String> osRelease = FileUtil.readFile("/etc/lsb-release");
            // Search for NAME=
            for (String line : osRelease) {
                if (line.startsWith("DISTRIB_DESCRIPTION=")) {
                    LOG.debug("lsb-release: {}", line);
                    line = ParseUtil.getDoubleQuoteStringValue(line).trim();
                    if (line.contains(" release ")) {
                        this.version = parseRelease(line, " release ");
                    }
                } else if (line.startsWith("DISTRIB_RELEASE=") && this.version == null) {
                    LOG.debug("lsb-release: {}", line);
                    this.version = ParseUtil.getDoubleQuoteStringValue(line).trim();
                } else if (line.startsWith("DISTRIB_CODENAME=") && this.codeName == null) {
                    LOG.debug("lsb-release: {}", line);
                    this.codeName = ParseUtil.getDoubleQuoteStringValue(line).trim();
                }
            }
        }
        return this.version != null;
    }

    /**
     * Attempts to read /etc/distrib-release (for some value of distrib)
     *
     * @return true if file successfully read and " release " or " VERSION " found
     */
    private boolean readDistribRelease(String filename) {
        if (new File(filename).exists()) {
            List<String> osRelease = FileUtil.readFile(filename);
            // Search for Distrib release x.x (Codename)
            for (String line : osRelease) {
                LOG.debug("{}: {}", filename, line);
                if (line.contains(" release ")) {
                    this.version = parseRelease(line, " release ");
                    // If this parses properly we're done
                    break;
                } else if (line.contains(" VERSION ")) {
                    this.version = parseRelease(line, " VERSION ");
                    // If this parses properly we're done
                    break;
                }
            }
        }
        return this.version != null;
    }

    /**
     * Helper method to parse version description line style
     *
     * @param line
     *            a String of the form "Distributor release x.x (Codename)"
     * @param splitLine
     *            A regex to split on, e.g. " release "
     * @return the parsed version (codeName may have also been set)
     */
    private String parseRelease(String line, String splitLine) {
        String[] split = line.split(splitLine);
        if (split.length > 1) {
            split = split[1].split("[()]");
            if (split.length > 0) {
                this.version = split[0].trim();
            }
            if (split.length > 1) {
                this.codeName = split[1].trim();
            }
        }
        return this.version;
    }
}
