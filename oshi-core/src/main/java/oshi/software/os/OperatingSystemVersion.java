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
package oshi.software.os;

/**
 * OperatingSystemVersion interface.
 * 
 * @deprecated Use {@link OperatingSystem.OSVersionInfo}
 */
@Deprecated
public interface OperatingSystemVersion {
    /**
     * <p>
     * getVersion.
     * </p>
     *
     * @return the version
     */
    String getVersion();

    /**
     * <p>
     * setVersion.
     * </p>
     *
     * @param version
     *            the version to set
     */
    void setVersion(String version);

    /**
     * <p>
     * getCodeName.
     * </p>
     *
     * @return the codeName
     */
    String getCodeName();

    /**
     * <p>
     * setCodeName.
     * </p>
     *
     * @param codeName
     *            the codeName to set
     */
    void setCodeName(String codeName);

    /**
     * <p>
     * getBuildNumber.
     * </p>
     *
     * @return the build number
     */
    String getBuildNumber();

    /**
     * <p>
     * setBuildNumber.
     * </p>
     *
     * @param buildNumber
     *            the build number to set
     */
    void setBuildNumber(String buildNumber);
}
