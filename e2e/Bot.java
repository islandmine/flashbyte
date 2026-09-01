import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protocol-level probe for the screenless server switch (Minecraft 26.1.x, protocol 775).
 *
 * <p>Logs into the proxy as an offline-mode player, waits for the first JoinGame, then issues
 * {@code /server <target>} for every comma-separated target and records what the proxy sends
 * afterwards. A seamless switch must produce no further JoinGame, Respawn or StartConfiguration
 * packet (each of those opens the vanilla loading screen), one game event 3 per switch and the
 * ForgetLevelChunk/LevelChunkWithLight pairs of the world reload.</p>
 *
 * <p>Run against a proxy with {@code compression-threshold = -1} and {@code online-mode = false}:
 * {@code javac Bot.java && java Bot lobby2,lobby1,lobby2 8000}. Exit code 0 means every switch
 * was seamless.</p>
 */
public class Bot {

    static final int PROTOCOL = 775;
    static final String HOST = "127.0.0.1";
    static final int PORT = 25577;
    static final String NAME = "SwitchBot";

    static final int C_LOGIN_SUCCESS = 0x02;
    static final int C_LOGIN_DISCONNECT = 0x00;
    static final int C_LOGIN_COMPRESSION = 0x03;

    static final int C_CFG_DISCONNECT = 0x02;
    static final int C_CFG_FINISH = 0x03;
    static final int C_CFG_KEEPALIVE = 0x04;
    static final int C_CFG_KNOWN_PACKS = 0x0E;
    static final int S_CFG_FINISH_ACK = 0x03;
    static final int S_CFG_KEEPALIVE = 0x04;
    static final int S_CFG_KNOWN_PACKS = 0x07;

    static final int C_PLAY_PLUGIN_MESSAGE = 0x18;
    static final int C_PLAY_DISCONNECT = 0x20;
    static final int C_PLAY_GAME_EVENT = 0x26;
    static final int C_PLAY_KEEPALIVE = 0x2C;
    static final int C_PLAY_JOIN_GAME = 0x31;
    static final int C_PLAY_RESPAWN = 0x52;
    static final int C_PLAY_START_CONFIGURATION = 0x76;
    static final int S_PLAY_CHAT_COMMAND = 0x07;
    static final int S_PLAY_KEEPALIVE = 0x1C;

    enum State { LOGIN, CONFIG, PLAY }

    static DataInputStream in;
    static DataOutputStream out;
    static State state = State.LOGIN;
    static long start = System.currentTimeMillis();

    static int joinGames;
    static int respawns;
    static int startConfigs;
    static int gameModeEvents;
    static long switchSentAt = -1;
    static int packetsAfterSwitch;
    static int bigPacketsAfterSwitch;
    static Map<Integer, Integer> idsAfterSwitch = new LinkedHashMap<>();
    static boolean disconnected;
    static int forgetChunks;
    static int chunks;

    public static void main(String[] args) throws Exception {
        String[] targets = (args.length > 0 ? args[0] : "lobby2").split(",");
        int targetIndex = 0;
        long observeMs = args.length > 1 ? Long.parseLong(args[1]) : 8000;
        Socket socket = new Socket(HOST, PORT);
        socket.setSoTimeout(15000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        ByteArrayOutputStream hs = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(hs);
        writeVarInt(h, PROTOCOL);
        writeString(h, HOST);
        h.writeShort(PORT);
        writeVarInt(h, 2);
        send(0x00, hs.toByteArray());

        ByteArrayOutputStream ls = new ByteArrayOutputStream();
        DataOutputStream l = new DataOutputStream(ls);
        writeString(l, NAME);
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
        l.writeLong(offline.getMostSignificantBits());
        l.writeLong(offline.getLeastSignificantBits());
        send(0x00, ls.toByteArray());

        long firstJoinAt = -1;
        while (true) {
            int length;
            try {
                length = readVarInt(in);
            } catch (EOFException e) {
                log("connection closed by remote");
                break;
            }
            byte[] payload = in.readNBytes(length);
            DataInputStream p = new DataInputStream(new java.io.ByteArrayInputStream(payload));
            int id = readVarInt(p);
            int bodyLength = payload.length - varIntSize(id);

            if (switchSentAt >= 0) {
                packetsAfterSwitch++;
                idsAfterSwitch.merge(id, 1, Integer::sum);
                if (bodyLength > 1000) bigPacketsAfterSwitch++;
            }

            switch (state) {
                case LOGIN -> {
                    if (id == C_LOGIN_SUCCESS) {
                        log("login success");
                        send(0x03, new byte[0]);
                        state = State.CONFIG;
                    } else if (id == C_LOGIN_DISCONNECT) {
                        log("login disconnect: " + readString(p));
                        disconnected = true;
                    } else if (id == C_LOGIN_COMPRESSION) {
                        log("FATAL: compression enabled, test expects threshold -1");
                        System.exit(2);
                    }
                }
                case CONFIG -> {
                    if (id == C_CFG_KEEPALIVE) {
                        long value = p.readLong();
                        ByteArrayOutputStream ka = new ByteArrayOutputStream();
                        new DataOutputStream(ka).writeLong(value);
                        send(S_CFG_KEEPALIVE, ka.toByteArray());
                    } else if (id == C_CFG_KNOWN_PACKS) {
                        ByteArrayOutputStream kp = new ByteArrayOutputStream();
                        writeVarInt(new DataOutputStream(kp), 0);
                        send(S_CFG_KNOWN_PACKS, kp.toByteArray());
                    } else if (id == C_CFG_FINISH) {
                        send(S_CFG_FINISH_ACK, new byte[0]);
                        state = State.PLAY;
                        log("configuration finished -> play");
                    } else if (id == C_CFG_DISCONNECT) {
                        log("config disconnect");
                        disconnected = true;
                    }
                }
                case PLAY -> {
                    if (id == C_PLAY_KEEPALIVE) {
                        long value = p.readLong();
                        ByteArrayOutputStream ka = new ByteArrayOutputStream();
                        new DataOutputStream(ka).writeLong(value);
                        send(S_PLAY_KEEPALIVE, ka.toByteArray());
                    } else if (id == C_PLAY_JOIN_GAME) {
                        joinGames++;
                        int entityId = p.readInt();
                        log("JOIN GAME #" + joinGames + " entityId=" + entityId + (switchSentAt >= 0 ? "  <-- AFTER SWITCH" : ""));
                        if (firstJoinAt < 0) firstJoinAt = System.currentTimeMillis();
                    } else if (id == C_PLAY_RESPAWN) {
                        respawns++;
                        log("RESPAWN #" + respawns + (switchSentAt >= 0 ? "  <-- AFTER SWITCH" : ""));
                    } else if (id == C_PLAY_START_CONFIGURATION) {
                        startConfigs++;
                        log("START CONFIGURATION (client would show reconfigure screen)" + (switchSentAt >= 0 ? "  <-- AFTER SWITCH" : ""));
                        send(0x10, new byte[0]);
                        state = State.CONFIG;
                    } else if (id == C_PLAY_GAME_EVENT) {
                        int event = p.readUnsignedByte();
                        float value = p.readFloat();
                        if (event == 3) gameModeEvents++;
                        log("game event " + event + " value=" + value + (switchSentAt >= 0 ? "  (+" + (System.currentTimeMillis() - switchSentAt) + " ms after switch)" : ""));
                    } else if (id == 0x25 && switchSentAt >= 0) {
                        forgetChunks++;
                    } else if (id == 0x2D && switchSentAt >= 0) {
                        chunks++;
                    } else if (id == C_PLAY_DISCONNECT) {
                        log("PLAY DISCONNECT");
                        disconnected = true;
                    }
                }
            }
            if (disconnected) break;

            long sinceAnchor = System.currentTimeMillis() - (switchSentAt >= 0 ? switchSentAt : firstJoinAt);
            if (firstJoinAt > 0 && targetIndex < targets.length && sinceAnchor > (switchSentAt >= 0 ? observeMs : 4000)) {
                ByteArrayOutputStream cmd = new ByteArrayOutputStream();
                writeString(new DataOutputStream(cmd), "server " + targets[targetIndex]);
                switchSentAt = System.currentTimeMillis();
                send(S_PLAY_CHAT_COMMAND, cmd.toByteArray());
                log("sent /server " + targets[targetIndex] + " (switch " + (targetIndex + 1) + "/" + targets.length + ")");
                targetIndex++;
            }
            if (switchSentAt >= 0 && targetIndex >= targets.length && System.currentTimeMillis() - switchSentAt > observeMs) {
                break;
            }
        }

        StringBuilder ids = new StringBuilder();
        idsAfterSwitch.forEach((k, v) -> ids.append(String.format("0x%02X=%d ", k, v)));
        log("---- RESULT ----");
        log("joinGames=" + joinGames + " respawns=" + respawns + " startConfigurations=" + startConfigs
                + " gameModeEvents=" + gameModeEvents + " disconnected=" + disconnected);
        log("packets after switch=" + packetsAfterSwitch + " (>1000 bytes: " + bigPacketsAfterSwitch + ") forgetLevelChunk=" + forgetChunks + " levelChunkWithLight=" + chunks);
        log("ids after switch: " + ids);
        boolean ok = joinGames == 1 && respawns == 0 && startConfigs == 0 && gameModeEvents >= targets.length
                && !disconnected && bigPacketsAfterSwitch >= 5 * targets.length;
        log(ok ? "SEAMLESS OK" : "SEAMLESS FAILED");
        System.exit(ok ? 0 : 1);
    }

    static void send(int id, byte[] body) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(packet);
        writeVarInt(d, id);
        d.write(body);
        byte[] bytes = packet.toByteArray();
        synchronized (out) {
            writeVarInt(out, bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    static void log(String message) {
        System.out.printf("[%6d ms] %s%n", System.currentTimeMillis() - start, message);
    }

    static void writeVarInt(DataOutputStream o, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            o.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        o.writeByte(value);
    }

    static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    static int readVarInt(DataInputStream i) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            byte b = i.readByte();
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
            if (position >= 32) throw new IOException("varint too big");
        }
    }

    static void writeString(DataOutputStream o, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(o, bytes.length);
        o.write(bytes);
    }

    static String readString(DataInputStream i) throws IOException {
        int length = readVarInt(i);
        return new String(i.readNBytes(length), StandardCharsets.UTF_8);
    }
}
