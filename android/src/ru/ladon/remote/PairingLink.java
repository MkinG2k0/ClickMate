package ru.ladon.remote;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;

/** Only local pairing links are accepted; QR contents are never executed. */
public final class PairingLink {
    public final String address, pin;
    private PairingLink(String address,String pin){this.address=address;this.pin=pin;}
    public static PairingLink parse(String value){
        try{
            if(value==null||value.length()>256)return null;
            URI uri=new URI(value);
            String scheme=uri.getScheme();if((!"clickmate".equalsIgnoreCase(scheme)&&!"ladon".equalsIgnoreCase(scheme))||!"connect".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null)return null;
            if(uri.getPath()!=null&&!uri.getPath().isEmpty()&&!uri.getPath().equals("/"))return null;
            if(uri.getRawQuery()==null)return null;
            HashMap<String,String> values=new HashMap<>();
            for(String part:uri.getRawQuery().split("&")){
                String[] pair=part.split("=",2);if(pair.length!=2)return null;
                String key=URLDecoder.decode(pair[0],"UTF-8"),val=URLDecoder.decode(pair[1],"UTF-8");
                if((!key.equals("ip")&&!key.equals("pin"))||values.put(key,val)!=null)return null;
            }
            String ip=values.get("ip"),pin=values.get("pin");
            return isPrivateAddress(ip)&&pin!=null&&pin.matches("[0-9]{4}")?new PairingLink(ip,pin):null;
        }catch(Exception ex){return null;}
    }
    public static boolean isPrivateAddress(String value){
        if(value==null)return false;
        try{String[] parts=value.split("\\.",-1);if(parts.length!=4)return false;int[] b=new int[4];for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;b[i]=Integer.parseInt(parts[i]);if(b[i]>255)return false;}return b[0]==10||(b[0]==192&&b[1]==168)||(b[0]==172&&b[1]>=16&&b[1]<=31)||(b[0]==169&&b[1]==254);}catch(Exception ex){return false;}
    }
}
