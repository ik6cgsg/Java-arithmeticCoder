import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Encoder {
    private final String delim = " ";
    private String decConfigFile;
    private Map<Byte, Double> probability;
    private Map<Byte, Segment> segs;
    private int textLen, numSeq;
    private enum confTypes {
        SEQUENCE_LEN,
        TEXT_LEN,
        PROBABILITY,
        DECODE_CONF
    }
    private String [] ids = {"num", "len", "prob", "decconf"};
    private Map<String, confTypes> configMap;

    Encoder() {
        probability = new HashMap<>();
        segs = new HashMap<>();
        textLen = 0;
        configMap = new HashMap<>();
        configMap.put(ids[0], confTypes.SEQUENCE_LEN);
        configMap.put(ids[1], confTypes.TEXT_LEN);
        configMap.put(ids[2], confTypes.PROBABILITY);
        configMap.put(ids[3], confTypes.DECODE_CONF);
    }

    private void setConfigs(String confFile) {
        try (BufferedReader configReader = new BufferedReader(new FileReader(confFile))) {
            String line;
            while ((line = configReader.readLine()) != null) {
                String[] words = line.split(delim);
                if (words.length < 2)
                    continue;

                switch (configMap.get(words[0])) {
                    case SEQUENCE_LEN: {
                        numSeq = Integer.parseInt(words[1]);
                        break;
                    }
                    case TEXT_LEN: {
                        textLen = Integer.parseInt(words[1]);
                        break;
                    }
                    case PROBABILITY: {
                        byte ch = (byte) Integer.parseInt(words[1]);
                        probability.put(ch, Double.parseDouble(words[2]));
                        segs.put(ch, new Segment());
                        break;
                    }
                    case DECODE_CONF: {
                        decConfigFile = words[1];
                        break;
                    }
                }
            }
            configReader.close();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }

    private void countProb(String srcFile) {
        try (DataInputStream srcReader = new DataInputStream(new FileInputStream(srcFile))) {
            while (srcReader.available() > 0) {
                byte ch = srcReader.readByte();
                textLen++;
                if (!probability.containsKey(ch))
                    probability.put(ch, 1.0);
                else
                    probability.replace(ch, probability.get(ch) + 1);

                segs.putIfAbsent(ch, new Segment());
            }
            srcReader.close();
        } catch (Exception ex) {
            System.out.print(ex);
        }

        for (Byte key : probability.keySet())
            probability.replace(key, probability.get(key) / textLen);
    }

    private void defineSegments() {
        double l = 0;

        for (Map.Entry<Byte, Segment> entry : segs.entrySet()) {
            entry.getValue().left = l;
            entry.getValue().right = l + probability.get(entry.getKey());
            l = entry.getValue().right;
        }
    }

    private void writeDecodeConf() {
        try {
            BufferedWriter encWriter = new BufferedWriter(new FileWriter(decConfigFile));
            encWriter.write("len " + textLen + "\n");
            encWriter.write("num " + numSeq + "\n");
            for (Map.Entry<Byte, Double> entry : probability.entrySet()) {
                encWriter.write("prob " + entry.getKey() + " " + entry.getValue() + "\n");
            }

            encWriter.close();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }

    public void encode(String srcFile, String dstFile, String confFile) {
        setConfigs(confFile);
        countProb(srcFile);
        defineSegments();

        int size = (int) Math.ceil((double) textLen / numSeq);

        try {
            DataInputStream srcReader = new DataInputStream(new FileInputStream(srcFile));
            BufferedWriter dstWriter = new BufferedWriter(new FileWriter(dstFile));

            for (int i = 0; i < size; i++) {
                double left = 0, right = 1;
                for (int j = 0; j < numSeq; j++) {
                    if (srcReader.available() <= 0)
                        break;
                    byte ch = srcReader.readByte();
                    double newR = left + (right - left) * segs.get(ch).right;
                    double newL = left + (right - left) * segs.get(ch).left;
                    right = newR;
                    left = newL;
                }
                dstWriter.write(String.valueOf((left + right) / 2) + "\n");
            }

            srcReader.close();
            dstWriter.close();
        } catch (Exception ex) {
            System.out.print(ex);
        }

        writeDecodeConf();
    }

    public void decode(String srcFile, String dstFile, String confFile) {
        setConfigs(confFile);
        defineSegments();

        int size = (int) Math.ceil((double) textLen / numSeq);

        try {
            BufferedReader srcReader = new BufferedReader(new FileReader(srcFile));
            DataOutputStream dstWriter = new DataOutputStream(new FileOutputStream(dstFile));

            for (int i = 0; i < size; i++) {
                double code = Double.parseDouble(srcReader.readLine());
                for (int j = 0; j < numSeq; j++) {
                    if (numSeq * i + j + 1 > textLen)
                        break;
                    for (Map.Entry<Byte, Segment> entry : segs.entrySet())
                        if (code >= entry.getValue().left && code < entry.getValue().right) {
                            dstWriter.writeByte(entry.getKey());
                            code = (code - entry.getValue().left) / (entry.getValue().right - entry.getValue().left);
                            break;
                        }
                }
            }

            srcReader.close();
            dstWriter.close();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
}
