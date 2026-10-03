import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import ru.ladon.remote.PairingLink;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class QrRoundTrip {
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);}
    public static void main(String[] args)throws Exception{
        BufferedImage image=ImageIO.read(new File(args[0]));int w=image.getWidth(),h=image.getHeight();int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        String value=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels)))).getText();
        PairingLink link=PairingLink.parse(value);check(link!=null&&link.address.equals("192.168.0.199")&&link.pin.equals("0123"),"Windows QR decoded with Android scanner library, leading-zero PIN preserved");
        check(value.startsWith("clickmate://connect?"),"new QR uses ClickMate link scheme");
        PairingLink legacy=PairingLink.parse("ladon://connect?ip=192.168.0.199&pin=0123");check(legacy!=null&&legacy.address.equals("192.168.0.199"),"legacy pairing links remain compatible");
        byte[] frame=new byte[w*h*3/2];java.util.Arrays.fill(frame,(byte)128);for(int i=0;i<w*h;i++)frame[i]=(byte)(pixels[i]&255);
        String cameraValue=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(frame,w,h,0,0,w,h,false)))).getText();
        check(value.equals(cameraValue),"camera NV21 luminance pipeline decodes Windows QR");
        String[] bad={"https://example.com", "ladon://connect?ip=8.8.8.8&pin=1234", "ladon://connect?ip=192.168.0.1&pin=123", "ladon://connect?ip=192.168.0.1&pin=1234&pin=0000", "ladon://connect?ip=192.168.0.1&pin=1234&cmd=run", "ladon://user@connect?ip=192.168.0.1&pin=1234", "ladon://connect:123?ip=192.168.0.1&pin=1234", "ladon://connect?ip=192.168.0.1&pin=1234#x", "ladon://connect?ip=192.168.0.999&pin=1234", "ladon://other?ip=192.168.0.1&pin=1234"};
        for(String s:bad)check(PairingLink.parse(s)==null,"invalid pairing link rejected");
    }
}
