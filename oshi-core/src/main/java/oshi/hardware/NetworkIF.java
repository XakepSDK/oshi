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
package oshi.hardware;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.SystemInfo;
import oshi.hardware.platform.linux.LinuxNetworks;
import oshi.hardware.platform.mac.MacNetworks;
import oshi.hardware.platform.unix.freebsd.FreeBsdNetworks;
import oshi.hardware.platform.unix.solaris.SolarisNetworks;
import oshi.hardware.platform.windows.WindowsNetworks;
import oshi.util.FormatUtil;
import oshi.util.ParseUtil;

/**
 * A network interface in the machine, including statistics
 */
public class NetworkIF {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkIF.class);

    private NetworkInterface networkInterface;
    private int mtu;
    private String mac;
    private String[] ipv4;
    private String[] ipv6;
    private long bytesRecv;
    private long bytesSent;
    private long packetsRecv;
    private long packetsSent;
    private long inErrors;
    private long outErrors;
    private long speed;
    private long timeStamp;

    /**
     * <p>
     * The NetworkInterface object.
     * </p>
     *
     * @return the network interface, an instance of
     *         {@link java.net.NetworkInterface}.
     */
    public NetworkInterface queryNetworkInterface() {
        return this.networkInterface;
    }

    /**
     * Sets the network interface and calculates other information derived from it
     *
     * @param networkInterface
     *            The network interface to set
     */
    public void setNetworkInterface(NetworkInterface networkInterface) {
        this.networkInterface = networkInterface;
        try {
            // Set MTU
            this.mtu = networkInterface.getMTU();
            // Set MAC
            byte[] hwmac = networkInterface.getHardwareAddress();
            if (hwmac != null) {
                List<String> octets = new ArrayList<>(6);
                for (byte b : hwmac) {
                    octets.add(String.format("%02x", b));
                }
                this.mac = String.join(":", octets);
            } else {
                this.mac = "Unknown";
            }
            // Set IP arrays
            ArrayList<String> ipv4list = new ArrayList<>();
            ArrayList<String> ipv6list = new ArrayList<>();
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (address.getHostAddress().length() > 0) {
                    if (address.getHostAddress().contains(":")) {
                        ipv6list.add(address.getHostAddress().split("%")[0]);
                    } else {
                        ipv4list.add(address.getHostAddress());
                    }
                }
            }
            this.ipv4 = ipv4list.toArray(new String[0]);
            this.ipv6 = ipv6list.toArray(new String[0]);
        } catch (SocketException e) {
            LOG.error("Socket exception: {}", e);
        }
    }

    /**
     * <p>
     * Interface name.
     * </p>
     *
     * @return The interface name.
     */
    public String getName() {
        return this.networkInterface.getName();
    }

    /**
     * <p>
     * Interface description.
     * </p>
     *
     * @return The description of the network interface. On some platforms, this is
     *         identical to the name.
     */
    public String getDisplayName() {
        return this.networkInterface.getDisplayName();
    }

    /**
     * <p>
     * The interface Maximum Transmission Unit (MTU).
     * </p>
     *
     * @return The MTU of the network interface. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the
     *         {@link #setNetworkInterface(NetworkInterface)} method
     */
    public int getMTU() {
        return this.mtu;
    }

    /**
     * <p>
     * The Media Access Control (MAC) address.
     * </p>
     *
     * @return The MAC Address. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the
     *         {@link #setNetworkInterface(NetworkInterface)} method
     */
    public String getMacaddr() {
        return this.mac;
    }

    /**
     * <p>
     * The Internet Protocol (IP) v4 address.
     * </p>
     *
     * @return The IPv4 Addresses. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the
     *         {@link #setNetworkInterface(NetworkInterface)} method
     */
    public String[] getIPv4addr() {
        return Arrays.copyOf(this.ipv4, this.ipv4.length);
    }

    /**
     * <p>
     * The Internet Protocol (IP) v6 address.
     * </p>
     *
     * @return The IPv6 Addresses. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the
     *         {@link #setNetworkInterface(NetworkInterface)} method
     */
    public String[] getIPv6addr() {
        return Arrays.copyOf(this.ipv6, this.ipv6.length);
    }

    /**
     * <p>
     * Getter for the field <code>bytesRecv</code>.
     * </p>
     *
     * @return The Bytes Received. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getBytesRecv() {
        return this.bytesRecv;
    }

    /**
     * <p>
     * Setter for the field <code>bytesRecv</code>.
     * </p>
     *
     * @param bytesRecv
     *            Set Bytes Received
     */
    public void setBytesRecv(long bytesRecv) {
        this.bytesRecv = ParseUtil.unsignedLongToSignedLong(bytesRecv);
    }

    /**
     * <p>
     * Getter for the field <code>bytesSent</code>.
     * </p>
     *
     * @return The Bytes Sent. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getBytesSent() {
        return this.bytesSent;
    }

    /**
     * <p>
     * Setter for the field <code>bytesSent</code>.
     * </p>
     *
     * @param bytesSent
     *            Set the Bytes Sent
     */
    public void setBytesSent(long bytesSent) {
        this.bytesSent = ParseUtil.unsignedLongToSignedLong(bytesSent);
    }

    /**
     * <p>
     * Getter for the field <code>packetsRecv</code>.
     * </p>
     *
     * @return The Packets Received. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getPacketsRecv() {
        return this.packetsRecv;
    }

    /**
     * <p>
     * Setter for the field <code>packetsRecv</code>.
     * </p>
     *
     * @param packetsRecv
     *            Set The Packets Received
     */
    public void setPacketsRecv(long packetsRecv) {
        this.packetsRecv = ParseUtil.unsignedLongToSignedLong(packetsRecv);
    }

    /**
     * <p>
     * Getter for the field <code>packetsSent</code>.
     * </p>
     *
     * @return The Packets Sent. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getPacketsSent() {
        return this.packetsSent;
    }

    /**
     * <p>
     * Setter for the field <code>packetsSent</code>.
     * </p>
     *
     * @param packetsSent
     *            Set The Packets Sent
     */
    public void setPacketsSent(long packetsSent) {
        this.packetsSent = ParseUtil.unsignedLongToSignedLong(packetsSent);
    }

    /**
     * <p>
     * Getter for the field <code>inErrors</code>.
     * </p>
     *
     * @return Input Errors. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getInErrors() {
        return this.inErrors;
    }

    /**
     * <p>
     * Setter for the field <code>inErrors</code>.
     * </p>
     *
     * @param inErrors
     *            The Input Errors to set.
     */
    public void setInErrors(long inErrors) {
        this.inErrors = ParseUtil.unsignedLongToSignedLong(inErrors);
    }

    /**
     * <p>
     * Getter for the field <code>outErrors</code>.
     * </p>
     *
     * @return The Output Errors. This value is set when the
     *         {@link oshi.hardware.NetworkIF} is instantiated and may not be up to
     *         date. To update this value, execute the {@link #updateAttributes()}
     *         method
     */
    public long getOutErrors() {
        return this.outErrors;
    }

    /**
     * <p>
     * Setter for the field <code>outErrors</code>.
     * </p>
     *
     * @param outErrors
     *            The Output Errors to set.
     */
    public void setOutErrors(long outErrors) {
        this.outErrors = ParseUtil.unsignedLongToSignedLong(outErrors);
    }

    /**
     * <p>
     * Getter for the field <code>speed</code>.
     * </p>
     *
     * @return The speed of the network interface in bits per second. This value is
     *         set when the {@link oshi.hardware.NetworkIF} is instantiated and may
     *         not be up to date. To update this value, execute the
     *         {@link #updateAttributes()} method
     */
    public long getSpeed() {
        return this.speed;
    }

    /**
     * <p>
     * Setter for the field <code>speed</code>.
     * </p>
     *
     * @param speed
     *            Set the speed of the network interface
     */
    public void setSpeed(long speed) {
        this.speed = ParseUtil.unsignedLongToSignedLong(speed);
    }

    /**
     * <p>
     * Getter for the field <code>timeStamp</code>.
     * </p>
     *
     * @return Returns the timeStamp.
     */
    public long getTimeStamp() {
        return this.timeStamp;
    }

    /**
     * <p>
     * Setter for the field <code>timeStamp</code>.
     * </p>
     *
     * @param timeStamp
     *            The timeStamp to set.
     */
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    /**
     * Updates interface network statistics on this interface. Statistics include
     * packets and bytes sent and received, and interface speed.
     */
    public void updateAttributes() {
        switch (SystemInfo.getCurrentPlatformEnum()) {
        case WINDOWS:
            WindowsNetworks.updateNetworkStats(this);
            break;
        case LINUX:
            LinuxNetworks.updateNetworkStats(this);
            break;
        case MACOSX:
            MacNetworks.updateNetworkStats(this);
            break;
        case SOLARIS:
            SolarisNetworks.updateNetworkStats(this);
            break;
        case FREEBSD:
            FreeBsdNetworks.updateNetworkStats(this);
            break;
        default:
            LOG.error("Unsupported platform. No update performed.");
            break;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(getName()).append(" ").append("(").append(getDisplayName()).append(")").append("\n");
        sb.append("  MAC Address: ").append(getMacaddr()).append("\n");
        sb.append("  MTU: ").append(getMTU()).append(", ").append("Speed: ").append(getSpeed()).append("\n");
        sb.append("  IPv4: ").append(Arrays.toString(getIPv4addr())).append("\n");
        sb.append("  IPv6: ").append(Arrays.toString(getIPv6addr())).append("\n");
        sb.append("  Traffic: received ").append(getPacketsRecv()).append(" packets/")
                .append(FormatUtil.formatBytes(getBytesRecv())).append(" (" + getInErrors() + " err);");
        sb.append(" transmitted ").append(getPacketsSent()).append(" packets/")
                .append(FormatUtil.formatBytes(getBytesSent())).append(" (" + getOutErrors() + " err)");
        return sb.toString();
    }
}
