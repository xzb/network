package tools;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xiezebin on 4/20/16.
 */
public class IO
{
    private static IO obInstance;
//    private static BufferedWriter obBufWriter;
//    private static BufferedReader obBufReader;
    private static Map<String, Long> obFileOffset;
    private final static String DIR = "file/";
    private IO()
    {
        obFileOffset = new HashMap<String, Long>();
        new File(DIR).mkdir();
    }
    public static IO instance()
    {
        if (obInstance == null)
        {
            obInstance = new IO();
        }
        return obInstance;
    }

    public String read(String arFile) {
        String content = null;
        try {
            FileReader fr = new FileReader(DIR + arFile);
            BufferedReader bfr = new BufferedReader(fr);

            Long loOffset = obFileOffset.get(arFile);
            if (loOffset == null)
            {
                loOffset = 0l;
            }
            loOffset = bfr.skip(loOffset);
            if ((content = bfr.readLine()) != null) {
                loOffset += content.toCharArray().length + 1;       //1 for "\n"
                obFileOffset.put(arFile, loOffset);
            }
            // TODO: close oldest when capacity reach 30
            bfr.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
        return content;
    }

    public void write(String arFile, String arContent)
    {
        try {
            System.out.println("Writing into " + arFile + ", " + arContent);
//            if (obBufWriter == null)
//            {
                FileWriter fw = new FileWriter(DIR + arFile, true);   //append
                BufferedWriter obBufWriter = new BufferedWriter(fw);
//            }
            obBufWriter.write(arContent + "\n");
            obBufWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public void terminateIO()
//    {
//        if (obBufWriter != null)
//        {
//            try {
//                obBufWriter.close();
//            } catch (IOException e)
//            {
//                e.printStackTrace();
//            }
//        }
//    }


}
