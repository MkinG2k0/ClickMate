using System;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Collections.Generic;

internal static class ProtocolTests {
    static int count;
    static int port;
    static void Check(bool result,string name) { if(!result) throw new Exception("FAIL: "+name); count++; Console.WriteLine("PASS: "+name); }
    sealed class Client : IDisposable {
        public TcpClient socket=new TcpClient();
        StreamReader reader; StreamWriter writer;
        public Client() { socket.Connect("127.0.0.1",port);socket.ReceiveTimeout=2000;reader=new StreamReader(socket.GetStream());writer=new StreamWriter(socket.GetStream(),new UTF8Encoding(false)){AutoFlush=true}; }
        public Dictionary<string,object> Call(string text) { writer.WriteLine(text);string line=reader.ReadLine();return line==null?null:new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(line); }
        public Dictionary<string,object> Resume(string token) { var first=Call("{\"type\":\"resume\"}");string challenge=first==null?null:first["challenge"] as string;return Call("{\"type\":\"resume_proof\",\"proof\":\""+TrustedPhone.CreateProof(token,challenge)+"\"}"); }
        public void Dispose() { socket.Close(); }
    }
    sealed class WebSocketClient : IDisposable {
        readonly TcpClient socket=new TcpClient();readonly Stream stream;
        public WebSocketClient() {
            socket.Connect("127.0.0.1",port);socket.ReceiveTimeout=2000;stream=socket.GetStream();
            string key=Convert.ToBase64String(Encoding.ASCII.GetBytes("clickmate-test-key"));
            byte[] request=Encoding.ASCII.GetBytes("GET /ws HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "+key+"\r\n\r\n");stream.Write(request,0,request.Length);
            var response=new List<byte>();int matched=0;byte[] ending={13,10,13,10};while(matched<4){int value=stream.ReadByte();if(value<0)throw new IOException();response.Add((byte)value);matched=value==ending[matched]?matched+1:(value==13?1:0);}
            string text=Encoding.ASCII.GetString(response.ToArray());if(!text.StartsWith("HTTP/1.1 101"))throw new IOException("WebSocket upgrade failed: "+text);
        }
        static void Exact(Stream stream,byte[] data,int count){int offset=0;while(offset<count){int read=stream.Read(data,offset,count-offset);if(read<=0)throw new IOException();offset+=read;}}
        public Dictionary<string,object> Call(string text) {
            byte[] payload=Encoding.UTF8.GetBytes(text),mask={11,29,47,71};var frame=new List<byte>{0x81};if(payload.Length<126)frame.Add((byte)(0x80|payload.Length));else{frame.Add(0xfe);frame.Add((byte)(payload.Length>>8));frame.Add((byte)payload.Length);}frame.AddRange(mask);for(int i=0;i<payload.Length;i++)frame.Add((byte)(payload[i]^mask[i%4]));byte[] outgoing=frame.ToArray();stream.Write(outgoing,0,outgoing.Length);
            byte[] head=new byte[2];Exact(stream,head,2);int length=head[1]&127;if(length==126){byte[] wide=new byte[2];Exact(stream,wide,2);length=(wide[0]<<8)|wide[1];}byte[] incoming=new byte[length];Exact(stream,incoming,length);return new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(Encoding.UTF8.GetString(incoming));
        }
        public void Dispose(){socket.Close();}
    }
    static string Pair(string pin) { return "{\"type\":\"pair\",\"pin\":\""+pin+"\"}"; }
    static bool Ok(Dictionary<string,object> r) { return r!=null && (bool)r["ok"]; }
    static int Main() {
        EmbeddedLibraries.Register();
        try { Run(); return 0; }
        catch(Exception ex) { Console.Error.WriteLine(ex.ToString()); return 1; }
    }
    static void Run() {
        Check(Program.StartHidden(new[]{"--tray"}) && Program.StartHidden(new[]{"--TRAY"}) && !Program.StartHidden(new string[0]),"tray startup argument is explicit and case insensitive");
        Check(StartupRegistration.Command("C:\\Apps\\ClickMate-PC.exe")=="\"C:\\Apps\\ClickMate-PC.exe\" --tray","startup command quotes executable path");
        string sampleToken=TrustedPhone.GenerateToken(),sampleHash=TrustedPhone.HashToken(TrustedPhone.GenerateToken());
        string challenge=TrustedPhone.GenerateChallenge(),proof=TrustedPhone.CreateProof(sampleToken,challenge);
        Check(sampleToken.Length>=40 && TrustedPhone.MatchesHash(TrustedPhone.HashToken(sampleToken),sampleToken) && !TrustedPhone.MatchesHash(sampleHash,sampleToken),"trusted phone tokens are random and compared by hash");
        Check(TrustedPhone.MatchesProof(TrustedPhone.HashToken(sampleToken),challenge,proof)&&!TrustedPhone.MatchesProof(sampleHash,challenge,proof),"trusted reconnect uses challenge proof without sending token");
        var discovery=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(DiscoveryService.CreateResponse("TEST-PC",48732));
        Check((string)discovery["type"]=="ladon-pc" && (string)discovery["name"]=="TEST-PC" && (int)discovery["port"]==48732,"discovery response identifies PC and port");
        using(var service=new DiscoveryService(0))using(var udp=new UdpClient()) {
            service.Start(48732);udp.Client.ReceiveTimeout=2000;byte[] probe=Encoding.ASCII.GetBytes(DiscoveryService.Request);udp.Send(probe,probe.Length,new System.Net.IPEndPoint(System.Net.IPAddress.Loopback,service.BoundPort));
            var sender=new System.Net.IPEndPoint(System.Net.IPAddress.Any,0);var response=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(Encoding.UTF8.GetString(udp.Receive(ref sender)));
            Check((string)response["type"]=="ladon-pc" && (int)response["port"]==48732,"discovery answers a local UDP probe");
        }
        using(var qr=PairingQr.Create("192.168.0.199","0123")) { qr.Save("build/pairing-test.png",System.Drawing.Imaging.ImageFormat.Png); Check(qr.Width>=200 && qr.Width==qr.Height,"QR bitmap generated"); }
        VerifyInputs();
        var server=new RemoteServer(delegate(string s){},0,false);string trustedToken=null;
        try {
            server.Start();
            port=server.BoundPort;
            Check(System.Text.RegularExpressions.Regex.IsMatch(server.Pin,"^[0-9]{4}$"),"four digit pairing code");
            using(var web=new System.Net.WebClient()) {
                string page=web.DownloadString("http://127.0.0.1:"+port+"/");
                string crypto=web.DownloadString("http://127.0.0.1:"+port+"/crypto.js");
                Check(page.Contains("ClickMate")&&page.Contains("touchpad")&&crypto.Contains("ClickMateCrypto"),"PWA shell and local trust code are served by Windows app");
            }
            using(var browser=new WebSocketClient()) {
                var hello=browser.Call(Pair(server.Pin));
                Check(Ok(hello)&&(int)hello["protocol"]==4,"browser pairs over WebSocket");
                Check(Ok(browser.Call("{\"type\":\"ping\"}")),"browser command receives WebSocket response");
            }
            Thread.Sleep(100);
            using(var c=new Client()) Check(!Ok(c.Call("{\"type\":\"ping\"}")),"commands rejected before pairing");
            using(var c=new Client()) Check(!Ok(c.Call(Pair("wrong"))),"wrong PIN rejected");
            using(var c=new Client()) {
                var hello=c.Call(Pair(server.Pin));
                Check(Ok(hello),"valid PIN accepted");
                Check((int)hello["protocol"]==4,"trusted phone protocol advertised");
                trustedToken=hello["token"] as string;Check(trustedToken!=null&&trustedToken.Length>=40,"first pairing issues a trusted phone token");
                Check(Ok(c.Call("{\"type\":\"ping\"}")),"paired heartbeat accepted");
                using(var other=new Client()) Check(!Ok(other.Call(Pair(server.Pin))),"second phone rejected");
                Check(!Ok(c.Call("{\"type\":\"shell\"}")),"unknown commands rejected");
                Check(!Ok(c.Call("{\"type\":\"move\",\"x\":99999,\"y\":0}")),"excessive mouse movement rejected");
                Check(!Ok(c.Call("{\"type\":\"key\",\"key\":\"arbitrary\"}")),"unknown keys rejected");
                Check(!Ok(c.Call("{\"type\":\"media\",\"key\":\"arbitrary\"}")),"unknown media rejected");
                Check(!Ok(c.Call("{\"type\":\"shortcut\",\"key\":\"arbitrary\"}")),"unknown shortcuts rejected");
                Check(Ok(c.Call("{\"type\":\"text\",\"text\":\"\"}")),"empty text is safe");
                server.Stop();
                bool closed=false;try{closed=c.Call("{\"type\":\"ping\"}")==null;}catch(IOException){closed=true;}
                Check(closed,"stop disconnects phone immediately");
            }
            server.Start();
            port=server.BoundPort;
            using(var c=new Client()) Check(!Ok(c.Resume("invalid-token")),"invalid trusted token rejected");
            using(var c=new Client()) Check(Ok(c.Resume(trustedToken)),"trusted phone reconnects after server restart without PIN");
            Thread.Sleep(100);
            using(var c=new Client()) { bool closed=false;try{closed=c.Call(new string('x',8300))==null;}catch(IOException){closed=true;} Check(closed,"oversized request disconnected"); }
            for(int i=0;i<5;i++)using(var c=new Client())Check(!Ok(c.Call(Pair("wrong"))),"PIN attempt "+(i+1));
            using(var c=new Client())Check(!Ok(c.Call(Pair(server.Pin))),"PIN guessing triggers cooldown");
            Check(!RemoteServer.Private(System.Net.IPAddress.Parse("8.8.8.8")),"public addresses excluded");
            Check(RemoteServer.Private(System.Net.IPAddress.Parse("192.168.1.10")),"LAN addresses accepted");
            Console.WriteLine(count+" checks passed");
        } finally { server.Stop(); }
    }
    static Input.INPUT[] Build(string type,string key) {
        return Input.Build(new Dictionary<string,object>{{"type",type},{"key",key}});
    }
    static void VerifyInputs() {
        string[] names={"mute","volumedown","volumeup","next","previous","stop","playpause"};
        ushort[] codes={0xAD,0xAE,0xAF,0xB0,0xB1,0xB2,0xB3};
        for(int i=0;i<names.Length;i++) {
            var events=Build("media",names[i]);
            Check(events.Length==2 && events[0].type==1 && events[0].data.key.vk==codes[i] && events[1].data.key.vk==codes[i] && events[0].data.key.flags==1 && events[1].data.key.flags==3,"media key press and release: "+names[i]);
        }
        var reopen=Build("shortcut","reopentab");
        Check(reopen.Length==6 && reopen[0].data.key.vk==17 && reopen[1].data.key.vk==16 && reopen[2].data.key.vk==84 && reopen[3].data.key.vk==84 && reopen[4].data.key.vk==16 && reopen[5].data.key.vk==17,"Ctrl Shift T modifiers released in reverse order");
        string[] shortcuts={"copy","paste","selectall","undo","cut","save","find","redo","newtab","closetab","reopentab","nexttab","prevtab","address","back","forward","zoomin","zoomout","zoomreset","desktop","switchwindow","present"};
        foreach(string name in shortcuts) {
            var events=Build("shortcut",name);bool balanced=events.Length%2==0;
            for(int i=0;i<events.Length/2;i++) balanced &= events[i].data.key.vk==events[events.Length-i-1].data.key.vk && (events[i].data.key.flags&2)==0 && (events[events.Length-i-1].data.key.flags&2)==2;
            Check(balanced,"balanced shortcut: "+name);
        }
        var middle=Input.Build(new Dictionary<string,object>{{"type","click"},{"button","middle"}});
        Check(middle.Length==2 && middle[0].data.mouse.flags==32 && middle[1].data.mouse.flags==64,"middle click press and release");
        var dragDown=Input.Build(new Dictionary<string,object>{{"type","button"},{"button","left"},{"state","down"}});
        var dragUp=Input.Build(new Dictionary<string,object>{{"type","button"},{"button","left"},{"state","up"}});
        Check(dragDown.Length==1 && dragDown[0].data.mouse.flags==2 && dragUp.Length==1 && dragUp[0].data.mouse.flags==4,"drag holds and releases left mouse button");
        var disconnectRelease=Input.BuildHeldMouseRelease();
        Check(disconnectRelease.Length==1 && disconnectRelease[0].data.mouse.flags==4,"disconnect releases only a held left button without synthetic right click");
        var text=Input.Build(new Dictionary<string,object>{{"type","text"},{"text","Я\n"}});
        Check(text.Length==4 && text[0].data.key.scan=='Я' && text[0].data.key.flags==4 && text[1].data.key.flags==6 && text[2].data.key.vk==13,"Unicode text and newline mapping");
    }
}
