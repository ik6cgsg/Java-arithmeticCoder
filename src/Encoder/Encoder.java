package Encoder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import Logger.Logger;

public class Encoder {
    private static final String splitDelim = " |:|="; // Delimiter in config file
    private static final String delim = " ";          // Delimiter in table of probabilities
    private static final String endl = "\n";
    private String tableFile;
    private Map<Byte, Double> probability;  // Map of letters to their probabilities
    private Map<Byte, Segment> segs;        // Map of letters to their segments
    private int textLen, numSeq;



    private enum targetType {
        ENCODE,
        DECODE
    }

    private enum confTypes {
        BLOCK_SIZE,
        SEQUENCE_LEN,
        TEXT_LEN,
        PROBABILITY,
        DECODE_CONF,
        TARGET,
        TABLE_FILE,
        TABLE_METHOD
    }
    private targetType target = targetType.ENCODE;
    private static final Map<String, targetType> tMap;          // Map target name to target type
    private static final Map<String, confTypes> configMap;      // Map config name to config type

    static {
        tMap = new HashMap<>();
        tMap.put("encode", targetType.ENCODE);
        tMap.put("decode", targetType.DECODE);

        configMap = new HashMap<>();
        configMap.put("num", confTypes.SEQUENCE_LEN);
        configMap.put("len", confTypes.TEXT_LEN);
        configMap.put("prob", confTypes.PROBABILITY);
        configMap.put("decconf", confTypes.DECODE_CONF);
        configMap.put("target", confTypes.TARGET);
        configMap.put("block", confTypes.BLOCK_SIZE);
        configMap.put("table", confTypes.TABLE_FILE);
        configMap.put("table_method", confTypes.TABLE_METHOD);
    }

    public Encoder(String confFile) throws IOException {
        probability = new HashMap<>();
        segs = new HashMap<>();
        textLen = 0;
        setConfigs(confFile);
    }

    /**
     * Set encoder configs from file
     * @param confFile
     * @throws IOException
     */
    private void setConfigs(String confFile) throws IOException {

        BufferedReader configReader = new BufferedReader(new FileReader(confFile));
        String line;
        while ((line = configReader.readLine()) != null) {
            String[] words = line.split(splitDelim);
            if (words.length != 2 && words.length != 3)
                throw new IOException("Wrong number of arguments in file: " + confFile + " at: " + line);
            confTypes type = configMap.get(words[0]);
            if (type == null)
                throw new IOException("Unknown config: " + words[0] + " in file: " + confFile + " at: " + line);
            switch (type) {
                case SEQUENCE_LEN: {
                    numSeq = Integer.parseInt(words[1]);
                    break;
                }
                case TEXT_LEN: {
                    textLen = Integer.parseInt(words[1]);
                    break;
                }
                case TABLE_FILE: {
                    tableFile = words[1];
                    break;
                }
                case PROBABILITY: {
                    byte ch = (byte) Integer.parseInt(words[1]);
                    probability.put(ch, Double.parseDouble(words[2]));
                    segs.put(ch, new Segment());
                    break;
                }
                case TARGET: {
                    target = tMap.get(words[1]);
                    if (target == null)
                        throw new IOException("Unknown target: " + words[1] + " in file: " + confFile + " at: " + line + " decode|encode expected");
                    break;
                }
            }
        }
        configReader.close();
        Logger.writeLn("Configs have been set");
    }

    /**
     * Count probabilities of letters in input file
     * @throws IOException
     */
    private void countProb(String input) throws IOException {
        DataInputStream copy = new DataInputStream(new FileInputStream(input));
        while (copy.available() > 0) {
            byte ch = copy.readByte();
            textLen++;
            if (!probability.containsKey(ch))
                probability.put(ch, 1.0);
            else
                probability.replace(ch, probability.get(ch) + 1);

            segs.putIfAbsent(ch, new Segment());
        }

        copy.close();

        for (Byte key : probability.keySet())
            probability.replace(key, probability.get(key) / textLen);
        Logger.writeLn("Probability have been counted");
    }

    /**
     * Set segments of letters
     */
    private void defineSegments() {
        double l = 0;

        for (Map.Entry<Byte, Segment> entry : segs.entrySet()) {
            entry.getValue().left = l;
            entry.getValue().right = l + probability.get(entry.getKey());
            l = entry.getValue().right;
        }
    }

    /**
     * Write table file
     * @throws IOException
     */
    private void writeDecodeConf(String tableFile) throws IOException {
        BufferedWriter encWriter = new BufferedWriter(new FileWriter(tableFile));

        for (Map.Entry<String, confTypes> entry : configMap.entrySet()) {
            switch (entry.getValue()) {
                case SEQUENCE_LEN: {
                    encWriter.write(entry.getKey() + delim + numSeq + endl);
                    break;
                }
                case PROBABILITY: {
                    for (Map.Entry<Byte, Double> prEntry : probability.entrySet()) {
                        encWriter.write(entry.getKey() + delim + prEntry.getKey() + delim + prEntry.getValue() + endl);
                    }
                    break;
                }
            }
        }
        encWriter.close();
    }

    /**
     * Code data
     * @param srcFile
     * @param dstFile
     */
    public void code(String srcFile, String dstFile) {
        switch (target) {
            case ENCODE: {
                try {
                    encode(srcFile, dstFile);
                } catch (IOException ex){
                    Logger.writeLn("Encoding Error!");
                    Logger.writeErrorLn(ex);
                    System.exit(1);
                }
                break;
            }
            case DECODE: {
                try {
                    decode(srcFile, dstFile);
                } catch (IOException ex) {
                    Logger.writeLn("Decoding Error!");
                    Logger.writeErrorLn(ex);
                    System.exit(1);
                }
                break;
            }
        }
    }

    /**
     * Encode data from srcFile
     * @param srcFile
     * @param dstFile
     * @throws IOException
     */
    private void encode(String srcFile, String dstFile) throws IOException {
        Logger.writeLn("Encoding...");
        countProb(srcFile);
        defineSegments();

        int size = (int) Math.ceil((double) textLen / numSeq);

        BufferedWriter dstWriter = new BufferedWriter(new FileWriter(dstFile));
        DataInputStream src = new DataInputStream(new FileInputStream(srcFile));

        for (int i = 0; i < size; i++) {
            double left = 0, right = 1;
            for (int j = 0; j < numSeq; j++) {
                if (src.available() <= 0)
                    break;
                byte ch = src.readByte();
                double newR = left + (right - left) * segs.get(ch).right;
                double newL = left + (right - left) * segs.get(ch).left;
                right = newR;
                left = newL;
            }
            dstWriter.write(String.valueOf((left + right) / 2) + endl);
        }
        Logger.writeLn("Encoding finished!!");
        src.close();
        dstWriter.close();
        writeDecodeConf(tableFile);
        Logger.writeLn("Decoding configurations are ready!");
    }

    /**
     * Encode data from srcFile
     * @param srcFile
     * @param dstFile
     * @throws IOException
     */
    private void decode(String srcFile, String dstFile) throws IOException {
        Logger.writeLn("Decoding...");
        defineSegments();

        int size = (int) Math.ceil((double) textLen / numSeq);

        BufferedReader srcReader = new BufferedReader(new FileReader(srcFile));
        DataOutputStream dstWriter = new DataOutputStream(new FileOutputStream(dstFile));

        for (int i = 0; i < size; i++) {
            double code = Double.parseDouble(srcReader.readLine());
            for (int j = 0; j < numSeq; j++) {
                if (numSeq * i + j == textLen)
                    break;
                for (Map.Entry<Byte, Segment> entry : segs.entrySet())
                    if (code >= entry.getValue().left && code < entry.getValue().right) {
                        dstWriter.writeByte(entry.getKey());
                        code = (code - entry.getValue().left) / (entry.getValue().right - entry.getValue().left);
                        break;
                    }
            }
        }
        Logger.writeLn("Decoding finished!!!");
        srcReader.close();
        dstWriter.close();
    }
}
