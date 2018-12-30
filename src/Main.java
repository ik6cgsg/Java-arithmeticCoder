import java.io.IOException;
import java.util.Map;
import Logger.Logger;
import Encoder.Encoder;

/**
 * Main class
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Error: wrong number of arguments!");
            System.exit(1);
        }
        // Parser init
        Parser par = new Parser();
        Map<Parser.valTypes, String> configs = null;
        // Try to parse main config file
        try {
            configs = par.parse(args[0]);
        } catch (IOException ex) {
            Logger.writeLn("Parsing Error!");
            Logger.writeErrorLn(ex);
            System.exit(1);
        }
        // Logger set
        Logger.setLogFile(configs.get(Parser.valTypes.LOG_FILE));
        try {
            Encoder enc = new Encoder(configs.get(Parser.valTypes.CONF_FILE));
            enc.code(configs.get(Parser.valTypes.SRC_FILE), configs.get(Parser.valTypes.DST_FILE));
        } catch (IOException ex) {
            Logger.writeLn("Encoder: setting configurations error!");
            Logger.writeErrorLn(ex);
        }
    }
}