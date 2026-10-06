using System;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Collections.Generic;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: System.Reflection.AssemblyVersion("0.10.0.0")]
[assembly: System.Reflection.AssemblyFileVersion("0.10.0.0")]

internal static class Program {
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr handle);
    internal static bool StartHidden(string[] args) { foreach(string arg in args)if(string.Equals(arg,"--tray",StringComparison.OrdinalIgnoreCase))return true;return false; }
    [STAThread] static void Main(string[] args) {
        EmbeddedLibraries.Register();
        bool first;
        using(var singleton = new Mutex(true,"Local\\Ladon.RemotePC.Instance",out first)) {
            if(!first) {
                try { using(var existing = EventWaitHandle.OpenExisting("Local\\Ladon.RemotePC.Show")) existing.Set(); } catch(WaitHandleCannotBeOpenedException) {}
                return;
            }
            using(var signal = new EventWaitHandle(false,EventResetMode.AutoReset,"Local\\Ladon.RemotePC.Show")) {
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                using(var form = new HostForm(StartHidden(args))) {
                    var wait = ThreadPool.RegisterWaitForSingleObject(signal,delegate(object state,bool timedOut) {
                        try { if(form.IsHandleCreated && !form.IsDisposed) form.BeginInvoke((Action)delegate { form.RestoreWindow();SetForegroundWindow(form.Handle); }); } catch(InvalidOperationException) {}
                    },null,Timeout.Infinite,false);
                    try { Application.Run(form); } finally { wait.Unregister(null); }
                }
            }
        }
    }
}

internal static class EmbeddedLibraries {
    internal static void Register() {
        AppDomain.CurrentDomain.AssemblyResolve += delegate(object sender,ResolveEventArgs args) {
            if(new System.Reflection.AssemblyName(args.Name).Name != "QRCoder") return null;
            using(var stream=System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("QRCoder.dll")) {
                if(stream==null)return null;
                using(var memory=new MemoryStream()){stream.CopyTo(memory);return System.Reflection.Assembly.Load(memory.ToArray());}
            }
        };
    }
}

internal static class PairingQr {
    internal static string Link(string ip,string pin) { return "clickmate://connect?ip=" + Uri.EscapeDataString(ip) + "&pin=" + Uri.EscapeDataString(pin); }
    internal static Bitmap Create(string ip,string pin) {
        using(var generator=new QRCoder.QRCodeGenerator())
        using(var data=generator.CreateQrCode(Link(ip,pin),QRCoder.QRCodeGenerator.ECCLevel.M))
        using(var code=new QRCoder.QRCode(data)) return code.GetGraphic(6);
    }
}

internal static class StartupRegistration {
    const string KeyPath="Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    const string ValueName="ClickMate";
    const string LegacyValueName="Ladon Remote PC";
    internal static string Command(string executable) { return "\""+executable+"\" --tray"; }
    internal static bool Enabled() {
        try { using(var key=Registry.CurrentUser.OpenSubKey(KeyPath,false)) { string value=key==null?null:key.GetValue(ValueName) as string;return string.Equals(value,Command(Application.ExecutablePath),StringComparison.OrdinalIgnoreCase); } }
        catch(Exception) { return false; }
    }
    internal static void Set(bool enabled) {
        using(var key=Registry.CurrentUser.CreateSubKey(KeyPath)) {
            if(enabled)key.SetValue(ValueName,Command(Application.ExecutablePath),RegistryValueKind.String);
            else key.DeleteValue(ValueName,false);
            key.DeleteValue(LegacyValueName,false);
        }
    }
    internal static void Migrate() {
        try { using(var key=Registry.CurrentUser.CreateSubKey(KeyPath)) { if(key.GetValue(ValueName)==null && key.GetValue(LegacyValueName)!=null)key.SetValue(ValueName,Command(Application.ExecutablePath),RegistryValueKind.String);key.DeleteValue(LegacyValueName,false); } } catch(Exception) {}
    }
}

internal static class TrustedPhone {
    const string KeyPath="Software\\ClickMate";
    const string ValueName="TrustedDeviceHashes";
    const string LegacyValueName="TrustedPhoneHash";
    internal static string GenerateToken() { byte[] value=new byte[32];using(var rng=RandomNumberGenerator.Create())rng.GetBytes(value);return Convert.ToBase64String(value); }
    internal static string GenerateChallenge() { byte[] value=new byte[24];using(var rng=RandomNumberGenerator.Create())rng.GetBytes(value);return Convert.ToBase64String(value); }
    internal static string HashToken(string token) { if(string.IsNullOrEmpty(token)||token.Length>128)return null;using(var sha=SHA256.Create())return Convert.ToBase64String(sha.ComputeHash(Encoding.UTF8.GetBytes(token))); }
    internal static bool MatchesHash(string expectedHash,string token) { string actual=HashToken(token);if(expectedHash==null||actual==null||expectedHash.Length!=actual.Length)return false;int different=0;for(int i=0;i<expectedHash.Length;i++)different|=expectedHash[i]^actual[i];return different==0; }
    internal static string CreateProof(string token,string challenge) { return CreateProofFromHash(HashToken(token),challenge); }
    internal static string CreateProofFromHash(string hash,string challenge) { try { using(var hmac=new HMACSHA256(Convert.FromBase64String(hash)))return Convert.ToBase64String(hmac.ComputeHash(Encoding.UTF8.GetBytes(challenge))); } catch(Exception) { return null; } }
    internal static bool MatchesProof(string expectedHash,string challenge,string proof) { string actual=CreateProofFromHash(expectedHash,challenge);if(actual==null||proof==null||actual.Length!=proof.Length)return false;int different=0;for(int i=0;i<actual.Length;i++)different|=actual[i]^proof[i];return different==0; }
    static List<string> Hashes(RegistryKey key) { var result=new List<string>();if(key==null)return result;string[] saved=key.GetValue(ValueName) as string[];if(saved!=null)foreach(string hash in saved)if(!string.IsNullOrEmpty(hash)&&!result.Contains(hash))result.Add(hash);string legacy=key.GetValue(LegacyValueName) as string;if(!string.IsNullOrEmpty(legacy)&&!result.Contains(legacy))result.Add(legacy);return result; }
    internal static string Issue() { string token=GenerateToken(),hash=HashToken(token);using(var key=Registry.CurrentUser.CreateSubKey(KeyPath)){var hashes=Hashes(key);hashes.Remove(hash);hashes.Add(hash);while(hashes.Count>8)hashes.RemoveAt(0);key.SetValue(ValueName,hashes.ToArray(),RegistryValueKind.MultiString);key.DeleteValue(LegacyValueName,false);}return token; }
    internal static bool VerifyProof(string challenge,string proof) { try { using(var key=Registry.CurrentUser.OpenSubKey(KeyPath,false))foreach(string hash in Hashes(key))if(MatchesProof(hash,challenge,proof))return true;return false; } catch(Exception) { return false; } }
    internal static bool Exists() { try { using(var key=Registry.CurrentUser.OpenSubKey(KeyPath,false))return Hashes(key).Count>0; } catch(Exception) { return false; } }
    internal static void Forget() { using(var key=Registry.CurrentUser.CreateSubKey(KeyPath)){key.DeleteValue(ValueName,false);key.DeleteValue(LegacyValueName,false);} }
}

internal sealed class DiscoveryService : IDisposable {
    public const int Port = 48733;
    public const string Request = "LADON_DISCOVER_V1";
    readonly int port;
    UdpClient socket;
    volatile bool running;
    public int BoundPort { get; private set; }

    public DiscoveryService(int port=Port) { this.port=port; }

    internal static string CreateResponse(string name,int port) {
        return new JavaScriptSerializer().Serialize(new { type="ladon-pc", name=name, port=port });
    }
    public void Start(int tcpPort) {
        var next = new UdpClient();
        next.Client.SetSocketOption(SocketOptionLevel.Socket,SocketOptionName.ReuseAddress,true);
        next.Client.Bind(new IPEndPoint(IPAddress.Any,port));
        BoundPort=((IPEndPoint)next.Client.LocalEndPoint).Port;
        socket=next;running=true;
        new Thread(new ThreadStart(delegate { Listen(next,tcpPort); })) { IsBackground=true }.Start();
    }
    void Listen(UdpClient source,int tcpPort) {
        byte[] expected=Encoding.ASCII.GetBytes(Request),reply=Encoding.UTF8.GetBytes(CreateResponse(Environment.MachineName,tcpPort));
        while(running && socket==source) {
            try {
                IPEndPoint remote=new IPEndPoint(IPAddress.Any,0);
                byte[] request=source.Receive(ref remote);
                if(!RemoteServer.Private(remote.Address) || request.Length!=expected.Length) continue;
                bool same=true;for(int i=0;i<expected.Length;i++)if(request[i]!=expected[i]){same=false;break;}
                if(same)source.Send(reply,reply.Length,remote);
            } catch(ObjectDisposedException) { break; }
            catch(SocketException) { if(!running || socket!=source)break; }
        }
    }
    public void Dispose() { running=false;var old=socket;socket=null;if(old!=null)old.Close(); }
}

internal static class WebRemote {
    const string WebSocketMagic="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    static readonly Dictionary<string,string[]> Assets=new Dictionary<string,string[]>(StringComparer.OrdinalIgnoreCase) {
        {"/",new[]{"ClickMate.Web.index.html","text/html; charset=utf-8"}},{"/index.html",new[]{"ClickMate.Web.index.html","text/html; charset=utf-8"}},
        {"/styles.css",new[]{"ClickMate.Web.styles.css","text/css; charset=utf-8"}},{"/crypto.js",new[]{"ClickMate.Web.crypto.js","application/javascript; charset=utf-8"}},{"/app.js",new[]{"ClickMate.Web.app.js","application/javascript; charset=utf-8"}},
        {"/manifest.webmanifest",new[]{"ClickMate.Web.manifest.webmanifest","application/manifest+json"}},{"/icon.svg",new[]{"ClickMate.Web.icon.svg","image/svg+xml"}},{"/sw.js",new[]{"ClickMate.Web.sw.js","application/javascript; charset=utf-8"}}
    };
    internal static bool Handle(TcpClient client,TcpListener source,string firstLine,StreamReader reader,RemoteServer server,out bool paired) {
        paired=false;string[] request=firstLine.Split(' ');if(request.Length<2||request[0]!="GET"){SendHttp(client.GetStream(),400,"text/plain; charset=utf-8",Encoding.UTF8.GetBytes("Bad request"));return true;}
        string path=request[1].Split('?')[0];var headers=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);string line;
        while(!string.IsNullOrEmpty(line=RemoteServer.Line(reader))){int colon=line.IndexOf(':');if(colon>0)headers[line.Substring(0,colon).Trim()]=line.Substring(colon+1).Trim();}
        if(path=="/ws"&&headers.ContainsKey("Upgrade")&&headers["Upgrade"].Equals("websocket",StringComparison.OrdinalIgnoreCase)) {
            string key; if(!headers.TryGetValue("Sec-WebSocket-Key",out key)){SendHttp(client.GetStream(),400,"text/plain",new byte[0]);return true;}
            string accept;using(var sha=SHA1.Create())accept=Convert.ToBase64String(sha.ComputeHash(Encoding.ASCII.GetBytes(key+WebSocketMagic)));
            byte[] response=Encoding.ASCII.GetBytes("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n");client.GetStream().Write(response,0,response.Length);
            var stream=client.GetStream();server.ServeSession(client,source,delegate{return ReadText(stream);},delegate(string text){WriteText(stream,text);},out paired);return true;
        }
        string[] asset;if(!Assets.TryGetValue(path,out asset)){SendHttp(client.GetStream(),404,"text/plain; charset=utf-8",Encoding.UTF8.GetBytes("Not found"));return true;}
        using(var embedded=System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream(asset[0])){if(embedded==null){SendHttp(client.GetStream(),500,"text/plain",new byte[0]);return true;}using(var memory=new MemoryStream()){embedded.CopyTo(memory);SendHttp(client.GetStream(),200,asset[1],memory.ToArray());}}
        return true;
    }
    static void SendHttp(Stream stream,int status,string contentType,byte[] body) { string reason=status==200?"OK":status==404?"Not Found":status==400?"Bad Request":"Server Error";string cache=contentType.StartsWith("text/html")?"no-cache":"public, max-age=3600";byte[] head=Encoding.ASCII.GetBytes("HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+contentType+"\r\nContent-Length: "+body.Length+"\r\nCache-Control: "+cache+"\r\nContent-Security-Policy: default-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:; style-src 'self'; script-src 'self'; manifest-src 'self'\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n");stream.Write(head,0,head.Length);stream.Write(body,0,body.Length);stream.Flush(); }
    static void Exact(Stream stream,byte[] buffer,int offset,int count) { while(count>0){int read=stream.Read(buffer,offset,count);if(read<=0)throw new IOException();offset+=read;count-=read;} }
    static string ReadText(Stream stream) {
        while(true){byte[] head=new byte[2];Exact(stream,head,0,2);bool final=(head[0]&128)!=0;int opcode=head[0]&15;bool masked=(head[1]&128)!=0;ulong length=(ulong)(head[1]&127);if(!final||!masked)throw new IOException();if(length==126){byte[] n=new byte[2];Exact(stream,n,0,2);length=(ulong)((n[0]<<8)|n[1]);}else if(length==127){byte[] n=new byte[8];Exact(stream,n,0,8);length=0;for(int i=0;i<8;i++)length=(length<<8)|n[i];}if(length>8192)throw new IOException();byte[] mask=new byte[4],payload=new byte[(int)length];Exact(stream,mask,0,4);Exact(stream,payload,0,payload.Length);for(int i=0;i<payload.Length;i++)payload[i]^=mask[i%4];if(opcode==8)throw new IOException();if(opcode==9){WriteFrame(stream,10,payload);continue;}if(opcode!=1)throw new IOException();return new UTF8Encoding(false,true).GetString(payload);}
    }
    static void WriteText(Stream stream,string text){WriteFrame(stream,1,Encoding.UTF8.GetBytes(text));}
    static void WriteFrame(Stream stream,int opcode,byte[] payload){var frame=new List<byte>();frame.Add((byte)(128|opcode));if(payload.Length<126)frame.Add((byte)payload.Length);else{frame.Add(126);frame.Add((byte)(payload.Length>>8));frame.Add((byte)payload.Length);}frame.AddRange(payload);byte[] data=frame.ToArray();stream.Write(data,0,data.Length);stream.Flush();}
}

internal sealed class HostForm : Form {
    readonly Label status = new Label();
    readonly Label code = new Label();
    readonly Button toggle = new Button();
    readonly ComboBox addresses = new ComboBox();
    readonly PictureBox qr = new PictureBox();
    readonly LinkLabel webAddress = new LinkLabel();
    readonly CheckBox autoStart = new CheckBox();
    readonly RemoteServer server;
    readonly NotifyIcon tray = new NotifyIcon();
    bool exiting,changingAutoStart;
    internal void RestoreWindow() { Opacity=1;ShowInTaskbar=true;Show();WindowState=FormWindowState.Normal;BringToFront();Activate(); }
    void HideToTray() { ShowInTaskbar=false;Hide(); }

    public HostForm(bool startHidden=false) {
        using(var stream=System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("Ladon.ico"))
            if(stream!=null) Icon=new Icon(stream);
        var menu=new ContextMenuStrip();
        menu.Items.Add("Открыть ClickMate",null,delegate { RestoreWindow(); });
        menu.Items.Add("Выход",null,delegate { exiting=true; Close(); });
        tray.Icon=Icon; tray.Text="ClickMate — управление ПК"; tray.ContextMenuStrip=menu; tray.Visible=true;
        tray.DoubleClick += delegate { RestoreWindow(); };
        Resize += delegate { if(WindowState==FormWindowState.Minimized) HideToTray(); };
        Text = "ClickMate — подключение"; ClientSize = new Size(480, 772);StartPosition=FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 11); BackColor = Color.FromArgb(238,242,246);
        FormBorderStyle = FormBorderStyle.FixedDialog; MaximizeBox = false;
        var title = new Label { Text = "ПК под рукой", Font = new Font("Segoe UI", 24, FontStyle.Bold), Bounds = new Rectangle(28,22,420,48) };
        var hint = new Label { Text = "Одна сеть Wi-Fi. Используйте Android-приложение\nили откройте веб-пульт по ссылке ниже.", Bounds = new Rectangle(30,78,420,52) };
        addresses.Bounds = new Rectangle(30,142,420,32); addresses.DropDownStyle=ComboBoxStyle.DropDownList;
        var list = new List<string>();
        foreach(var adapter in NetworkInterface.GetAllNetworkInterfaces()) {
            if(adapter.OperationalStatus != OperationalStatus.Up || adapter.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            string description = (adapter.Name + " " + adapter.Description).ToLowerInvariant();
            if(adapter.NetworkInterfaceType != NetworkInterfaceType.Wireless80211 && adapter.NetworkInterfaceType != NetworkInterfaceType.Ethernet) continue;
            if(description.Contains("tun") || description.Contains("tap") || description.Contains("vpn") || description.Contains("virtual") || description.Contains("vmware") || description.Contains("hyper-v")) continue;
            foreach(var a in adapter.GetIPProperties().UnicastAddresses)
                if(a.Address.AddressFamily == AddressFamily.InterNetwork && RemoteServer.Private(a.Address)) list.Add(a.Address.ToString() + (adapter.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? "  (Wi-Fi)" : "  (Ethernet)"));
        }
        addresses.Items.AddRange(list.ToArray()); if(list.Count>0)addresses.SelectedIndex=0;
        webAddress.Bounds=new Rectangle(30,180,420,26);webAddress.Text="Веб-пульт: адрес появится после запуска";
        webAddress.LinkClicked += delegate { if(addresses.SelectedItem==null)return;try{Clipboard.SetText("http://"+addresses.SelectedItem.ToString().Split(' ')[0]+":"+server.BoundPort+"/");status.Text="Адрес веб-пульта скопирован";}catch(Exception){} };
        qr.Bounds=new Rectangle(120,212,240,240);qr.BackColor=Color.White;qr.SizeMode=PictureBoxSizeMode.CenterImage;
        var manual = new Label { Text="Для приложения: QR либо адрес и код вручную",Bounds=new Rectangle(30,467,420,26) };
        code.Bounds = new Rectangle(28,497,425,48); code.Font = new Font("Segoe UI",24,FontStyle.Bold);
        status.Bounds = new Rectangle(30,553,420,43);
        toggle.Bounds = new Rectangle(30,599,420,44); toggle.FlatStyle = FlatStyle.Flat;
        toggle.BackColor = Color.FromArgb(41,84,139); toggle.ForeColor = Color.White;
        toggle.Click += delegate { if(server.Running) Stop(); else Start(); };
        StartupRegistration.Migrate();autoStart.Text="Запускать вместе с Windows · сразу в трее";autoStart.Bounds=new Rectangle(34,653,410,32);autoStart.Checked=StartupRegistration.Enabled();
        autoStart.CheckedChanged += delegate { if(changingAutoStart)return;try{StartupRegistration.Set(autoStart.Checked);}catch(Exception ex){changingAutoStart=true;autoStart.Checked=!autoStart.Checked;changingAutoStart=false;MessageBox.Show(this,"Не удалось изменить автозапуск:\n"+ex.Message,"ClickMate",MessageBoxButtons.OK,MessageBoxIcon.Warning);} };
        var forget=new Button { Text="Забыть доверенные устройства",Bounds=new Rectangle(30,700,420,42),FlatStyle=FlatStyle.Flat,BackColor=Color.White };
        forget.Click += delegate { ForgetTrustedPhone(); };
        Controls.AddRange(new Control[] { title,hint,addresses,webAddress,qr,manual,code,status,toggle,autoStart,forget });
        server = new RemoteServer(SetStatus);
        addresses.SelectedIndexChanged += delegate { UpdateQr(); };
        if(startHidden){WindowState=FormWindowState.Minimized;ShowInTaskbar=false;Opacity=0;}
        Shown += delegate { Start();if(startHidden)BeginInvoke((Action)HideToTray); };
        FormClosing += delegate(object sender,FormClosingEventArgs e) {
            if(!exiting && e.CloseReason==CloseReason.UserClosing) { e.Cancel=true; HideToTray(); return; }
            server.Stop(); tray.Visible=false; tray.Dispose(); if(qr.Image!=null)qr.Image.Dispose();
        };
    }
    void SetStatus(string text) {
        if(IsDisposed || !IsHandleCreated) return;
        try { BeginInvoke((Action)delegate { if(!IsDisposed) status.Text = text; }); } catch(InvalidOperationException) {}
    }
    void Start() {
        try { server.Start(); code.Text = "Код: " + server.Pin; toggle.Text = "Отключить доступ"; status.Text = addresses.Items.Count==0 ? "Подключитесь к Wi-Fi и откройте приложение снова." : "Ожидание телефона"; UpdateQr(); }
        catch(SocketException ex) { status.Text = ex.SocketErrorCode==SocketError.AddressAlreadyInUse ? "Порт занят. Закройте старую версию ClickMate и нажмите «Повторить»." : "Не удалось открыть подключение: " + ex.Message; toggle.Text = "Повторить"; }
        catch(Exception ex) { server.Stop(); status.Text="Не удалось включить доступ: "+ex.Message;toggle.Text="Повторить"; }
    }
    void UpdateQr() { var old=qr.Image;qr.Image=null;if(old!=null)old.Dispose();if(server.Running && addresses.SelectedItem!=null){string ip=addresses.SelectedItem.ToString().Split(' ')[0];qr.Image=PairingQr.Create(ip,server.Pin);webAddress.Text="Веб-пульт: http://"+ip+":"+server.BoundPort+"/";}else webAddress.Text="Веб-пульт недоступен"; }
    void Stop() { server.Stop(); UpdateQr(); code.Text = "Доступ отключён"; status.Text = "Телефон отключён"; toggle.Text = "Включить доступ"; }
    void ForgetTrustedPhone() {
        if(!TrustedPhone.Exists()){MessageBox.Show(this,"Доверенные устройства пока не сохранены.","ClickMate",MessageBoxButtons.OK,MessageBoxIcon.Information);return;}
        if(MessageBox.Show(this,"Все телефоны и браузеры потеряют автоматический доступ. Продолжить?","Забыть устройства",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;
        try { TrustedPhone.Forget();if(server.Running){server.Stop();Start();}MessageBox.Show(this,"Доверие удалено. Для следующего подключения потребуется новый код.","ClickMate",MessageBoxButtons.OK,MessageBoxIcon.Information); }
        catch(Exception ex){MessageBox.Show(this,"Не удалось удалить доверие:\n"+ex.Message,"ClickMate",MessageBoxButtons.OK,MessageBoxIcon.Warning);}
    }
}

internal sealed class RemoteServer {
    public const int Port = 48732;
    public volatile bool Running;
    public string Pin { get; private set; }
    public int BoundPort { get; private set; }
    readonly int port;
    TcpListener listener;
    DiscoveryService discovery;
    TcpClient active;
    readonly object gate = new object();
    readonly Action<string> status;
    readonly bool persistentTrust;
    string sessionTrustedHash;
    int workers, failures;
    DateTime lockedUntil;
    public RemoteServer(Action<string> status, int port = Port, bool persistentTrust = true) { this.status = status; this.port = port; this.persistentTrust=persistentTrust; }
    string IssueTrustedToken() { if(persistentTrust)return TrustedPhone.Issue();string token=TrustedPhone.GenerateToken();sessionTrustedHash=TrustedPhone.HashToken(token);return token; }
    bool VerifyTrustedProof(string challenge,string proof) { return persistentTrust?TrustedPhone.VerifyProof(challenge,proof):TrustedPhone.MatchesProof(sessionTrustedHash,challenge,proof); }
    public static bool Private(IPAddress address) {
        byte[] b = address.GetAddressBytes();
        return b.Length == 4 && (b[0] == 10 || b[0] == 127 || (b[0] == 192 && b[1] == 168) || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 169 && b[1] == 254));
    }
    public void Start() {
        byte[] bytes = new byte[4]; using(var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
        Pin = (BitConverter.ToUInt32(bytes,0) % 10000).ToString("D4");
        var next = new TcpListener(IPAddress.Any,port); next.Start(4);
        BoundPort = ((IPEndPoint)next.LocalEndpoint).Port;
        listener = next; Running = true;
        if(port==Port) {
            try { discovery=new DiscoveryService();discovery.Start(BoundPort); }
            catch(SocketException) { if(discovery!=null)discovery.Dispose();discovery=null; }
        }
        new Thread(delegate() { Accept(next); }) { IsBackground = true }.Start();
    }
    public void Stop() {
        lock(gate) { Running = false; if(discovery!=null)discovery.Dispose();discovery=null;if(listener != null) listener.Stop(); if(active != null) active.Close(); active = null; }
    }
    void Accept(TcpListener source) {
        while(Running && listener == source) {
            TcpClient client;
            try { client = source.AcceptTcpClient(); } catch { break; }
            if(!Private(((IPEndPoint)client.Client.RemoteEndPoint).Address) || Interlocked.Increment(ref workers) > 4) {
                if(Private(((IPEndPoint)client.Client.RemoteEndPoint).Address)) Interlocked.Decrement(ref workers);
                client.Close(); continue;
            }
            ThreadPool.QueueUserWorkItem(delegate { try { Serve(client,source); } finally { Interlocked.Decrement(ref workers); } });
        }
    }
    internal static string Line(StreamReader reader) {
        var value = new StringBuilder();
        for(int i=0;i<8192;i++) { int ch=reader.Read(); if(ch < 0) throw new IOException(); if(ch == '\n') return value.ToString(); if(ch != '\r') value.Append((char)ch); }
        throw new IOException("Message too long");
    }
    void Serve(TcpClient client, TcpListener source) {
        bool paired = false;
        try {
            client.NoDelay = true; client.ReceiveTimeout = 6000; client.SendTimeout = 3000;
            using(client)
            using(var reader = new StreamReader(client.GetStream(),new UTF8Encoding(false,true),false,1024,true))
            using(var writer = new StreamWriter(client.GetStream(),new UTF8Encoding(false),1024,true) { AutoFlush = true }) {
                string firstLine=Line(reader);if(firstLine.StartsWith("GET ",StringComparison.Ordinal)){WebRemote.Handle(client,source,firstLine,reader,this,out paired);return;}
                ServeSession(client,source,delegate{return Line(reader);},delegate(string text){writer.WriteLine(text);},out paired,firstLine);
            }
        } catch(Exception) { /* A closed, malformed or timed-out connection is disconnected. */ }
        finally {
            if(paired)Input.ReleaseMouseButtons();
            lock(gate) { if(paired && active == client) { active = null; if(Running) status("Устройство отключено · ожидание подключения"); } }
            client.Close();
        }
    }
    internal void ServeSession(TcpClient client,TcpListener source,Func<string> receive,Action<string> send,out bool paired,string firstLine=null) {
                paired=false;var json = new JavaScriptSerializer { MaxJsonLength = 8192, RecursionLimit = 8 };
                var hello = json.Deserialize<Dictionary<string,object>>(firstLine??receive());
                bool resumeAttempt=Get(hello,"type")=="resume",resumeValid=false;
                if(resumeAttempt) {
                    string challenge=TrustedPhone.GenerateChallenge();send(json.Serialize(new { ok=true, challenge=challenge }));
                    var proof=json.Deserialize<Dictionary<string,object>>(receive());resumeValid=Get(proof,"type")=="resume_proof"&&VerifyTrustedProof(challenge,Get(proof,"proof"));
                }
                string error = null,issuedToken=null;bool firstPairing=false;
                lock(gate) {
                    if(!Running || listener != source) return;
                    if(DateTime.UtcNow < lockedUntil) error = "Слишком много попыток. Подождите 30 секунд.";
                    else if((Get(hello,"type")!="pair" || Get(hello,"pin")!=Pin) && (!resumeAttempt || !resumeValid)) {
                        failures++; if(failures >= 5) { lockedUntil = DateTime.UtcNow.AddSeconds(30); failures = 0; }
                        error = Get(hello,"type")=="resume"?"Телефон больше не доверен. Подключитесь по новому коду.":"Неверный код подключения";
                    } else if(active != null) error = "Другой телефон уже подключён";
                    else { active = client; paired = true; failures = 0;firstPairing=Get(hello,"type")=="pair";if(firstPairing)issuedToken=IssueTrustedToken(); }
                }
                if(error != null) { send(json.Serialize(new { ok=false, error=error })); return; }
                send(json.Serialize(new { ok=true, name=Environment.MachineName, protocol=4, token=issuedToken }));
                status("Устройство подключено: " + ((IPEndPoint)client.Client.RemoteEndPoint).Address);
                client.ReceiveTimeout = 15000;
                while(Running && listener == source) {
                    var command = json.Deserialize<Dictionary<string,object>>(receive());
                    lock(gate) {
                        if(!Running || active != client || listener != source) break;
                        try { Input.Apply(command); send("{\"ok\":true}"); }
                        catch(ArgumentException) { send("{\"ok\":false,\"error\":\"Недопустимая команда\"}"); }
                        catch(InvalidOperationException) { send("{\"ok\":false,\"error\":\"Windows заблокировала ввод в это окно\"}"); }
                    }
                }
    }
    internal static string Get(Dictionary<string,object> command,string key) { object value; return command != null && command.TryGetValue(key,out value) ? value as string : null; }
}

internal static class Input {
    [StructLayout(LayoutKind.Sequential)] internal struct INPUT { public uint type; public UNION data; }
    [StructLayout(LayoutKind.Explicit)] internal struct UNION { [FieldOffset(0)] public MOUSE mouse; [FieldOffset(0)] public KEY key; }
    [StructLayout(LayoutKind.Sequential)] internal struct MOUSE { public int dx,dy; public uint mouseData,flags,time; public UIntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] internal struct KEY { public ushort vk,scan; public uint flags,time; public UIntPtr extra; }
    [DllImport("user32.dll",SetLastError=true)] static extern uint SendInput(uint count,INPUT[] input,int size);
    static bool leftButtonDown;
    static readonly Dictionary<string,ushort> Keys = new Dictionary<string,ushort> {
        {"enter",13},{"backspace",8},{"tab",9},{"escape",27},{"space",32},
        {"left",37},{"up",38},{"right",39},{"down",40},{"delete",46},
        {"home",36},{"end",35},{"pageup",33},{"pagedown",34},{"f5",116},{"f11",122}
    };
    static readonly Dictionary<string,ushort> Media = new Dictionary<string,ushort> {
        {"mute",0xAD},{"volumedown",0xAE},{"volumeup",0xAF},
        {"next",0xB0},{"previous",0xB1},{"stop",0xB2},{"playpause",0xB3}
    };
    static readonly Dictionary<string,ushort[]> Shortcuts = new Dictionary<string,ushort[]> {
        {"copy",new ushort[]{17,67}},{"paste",new ushort[]{17,86}},
        {"selectall",new ushort[]{17,65}},{"undo",new ushort[]{17,90}},
        {"cut",new ushort[]{17,88}},{"save",new ushort[]{17,83}},
        {"find",new ushort[]{17,70}},{"redo",new ushort[]{17,89}},
        {"newtab",new ushort[]{17,84}},{"closetab",new ushort[]{17,87}},
        {"reopentab",new ushort[]{17,16,84}},{"nexttab",new ushort[]{17,9}},
        {"prevtab",new ushort[]{17,16,9}},{"address",new ushort[]{17,76}},
        {"back",new ushort[]{18,37}},{"forward",new ushort[]{18,39}},
        {"zoomin",new ushort[]{17,187}},{"zoomout",new ushort[]{17,189}},
        {"zoomreset",new ushort[]{17,48}},{"desktop",new ushort[]{91,68}},
        {"switchwindow",new ushort[]{18,9}},{"present",new ushort[]{16,116}}
    };
    static int Number(Dictionary<string,object> c,string name,int limit) {
        object obj; if(!c.TryGetValue(name,out obj) || !(obj is int)) throw new ArgumentException();
        int n=(int)obj; if(n < -limit || n > limit) throw new ArgumentException(); return n;
    }
    static INPUT Mouse(int x,int y,uint flags,uint data) { return new INPUT { type=0,data=new UNION { mouse=new MOUSE { dx=x,dy=y,flags=flags,mouseData=data } } }; }
    static INPUT Key(ushort vk,ushort scan,uint flags) { return new INPUT { type=1,data=new UNION { key=new KEY { vk=vk,scan=scan,flags=flags } } }; }
    static bool Extended(ushort vk) { return (vk>=33 && vk<=40) || vk==46 || vk==91 || (vk>=0xAD && vk<=0xB3); }
    static INPUT VirtualKey(ushort vk,bool up) { return Key(vk,0,(Extended(vk)?1u:0u)|(up?2u:0u)); }
    static INPUT[] Press(params ushort[] keys) {
        var events = new List<INPUT>();
        foreach(ushort key in keys) events.Add(VirtualKey(key,false));
        for(int i=keys.Length-1;i>=0;i--) events.Add(VirtualKey(keys[i],true));
        return events.ToArray();
    }
    static void Send(INPUT[] inputs) { if(inputs.Length>0 && SendInput((uint)inputs.Length,inputs,Marshal.SizeOf(typeof(INPUT))) != inputs.Length) throw new InvalidOperationException(); }
    public static void Apply(Dictionary<string,object> c) {
        Send(Build(c));
        if(RemoteServer.Get(c,"type")=="button") leftButtonDown=RemoteServer.Get(c,"state")=="down";
    }
    internal static INPUT[] BuildHeldMouseRelease() { return new[]{Mouse(0,0,4,0)}; }
    internal static void ReleaseMouseButtons() {
        if(!leftButtonDown)return;
        try { Send(BuildHeldMouseRelease()); } catch(InvalidOperationException) {}
        finally { leftButtonDown=false; }
    }
    internal static INPUT[] Build(Dictionary<string,object> c) {
        string type=RemoteServer.Get(c,"type");
        switch(type) {
            case "ping": return new INPUT[0];
            case "move": return new[]{Mouse(Number(c,"x",1000),Number(c,"y",1000),1,0)};
            case "scroll": return new[]{Mouse(0,0,0x0800,unchecked((uint)Number(c,"amount",1200)))};
            case "click":
                string button=RemoteServer.Get(c,"button");
                if(button == "left") return new[]{Mouse(0,0,2,0),Mouse(0,0,4,0)};
                if(button == "right") return new[]{Mouse(0,0,8,0),Mouse(0,0,16,0)};
                if(button == "middle") return new[]{Mouse(0,0,32,0),Mouse(0,0,64,0)};
                throw new ArgumentException();
            case "button":
                string heldButton=RemoteServer.Get(c,"button"),state=RemoteServer.Get(c,"state");
                if(heldButton!="left" || (state!="down" && state!="up"))throw new ArgumentException();
                return new[]{Mouse(0,0,state=="down"?2u:4u,0)};
            case "key":
                ushort vk; if(!Keys.TryGetValue(RemoteServer.Get(c,"key") ?? "",out vk)) throw new ArgumentException();
                return Press(vk);
            case "media":
                ushort media; if(!Media.TryGetValue(RemoteServer.Get(c,"key") ?? "",out media)) throw new ArgumentException();
                return Press(media);
            case "shortcut":
                ushort[] shortcut;
                if(!Shortcuts.TryGetValue(RemoteServer.Get(c,"key") ?? "",out shortcut)) throw new ArgumentException();
                return Press(shortcut);
            case "text":
                string text=RemoteServer.Get(c,"text"); if(text==null || text.Length>1000) throw new ArgumentException();
                var events = new List<INPUT>();
                foreach(char ch in text) { if(ch == '\r') continue; if(ch == '\n') { events.Add(Key(13,0,0)); events.Add(Key(13,0,2)); } else { events.Add(Key(0,ch,4)); events.Add(Key(0,ch,6)); } }
                return events.ToArray();
            default: throw new ArgumentException();
        }
    }
}
