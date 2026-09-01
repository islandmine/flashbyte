package com.velocitypowered.proxy.connection;

import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wire format of the {@code islandmine:bridge} plugin message channel shared with the paper core
 * plugin: a modified-UTF key followed by a count of modified-UTF key/value pairs. The proxy uses it
 * to ask the backend a player is leaving to tear down that player's client-side world state before
 * a seamless switch and to learn when that is done.
 */
public final class SeamlessBridge {

  public static final String CHANNEL = "islandmine:bridge";
  public static final String PREPARE = "switch.prepare";
  public static final String READY = "switch.ready";

  private SeamlessBridge() {
  }

  /**
   * Whether a plugin message channel is the islandmine bridge channel.
   *
   * @param channel the channel name
   * @return true for {@value #CHANNEL}
   */
  public static boolean isBridgeChannel(String channel) {
    return CHANNEL.equals(channel);
  }

  /**
   * Encodes a bridge message as a plugin message packet.
   *
   * @param key  the message key
   * @param data the message fields
   * @return the packet to send
   */
  public static PluginMessagePacket message(String key, Map<String, String> data) {
    ByteBuf buf = Unpooled.buffer();
    try (DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(buf))) {
      out.writeUTF(key);
      out.writeInt(data.size());
      for (Map.Entry<String, String> entry : data.entrySet()) {
        out.writeUTF(entry.getKey());
        out.writeUTF(entry.getValue());
      }
    } catch (IOException e) {
      buf.release();
      throw new IllegalStateException(e);
    }
    return new PluginMessagePacket(CHANNEL, buf);
  }

  /**
   * Reads the message key of an encoded bridge message without consuming the buffer.
   *
   * @param content the plugin message payload
   * @return the key, or null if the payload is not a bridge message
   */
  public static @Nullable String key(ByteBuf content) {
    ByteBuf slice = content.slice();
    try (DataInputStream in = new DataInputStream(new ByteBufInputStream(slice))) {
      return in.readUTF();
    } catch (IOException e) {
      return null;
    }
  }
}
