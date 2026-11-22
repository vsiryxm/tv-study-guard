package cn.studyguard.tv;

import android.content.Context;
import cn.studyguard.core.PairingGate;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;

import java.security.*;
import java.util.*;
import java.util.concurrent.*;

final class ParentServer {
    static final int PORT=18765;
    private static volatile ParentServer active;
    private static volatile String startupError="";
    private final Store store;
    private final Context context;
    private final byte[] page;
    private final ExecutorService clients=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12));
    private final Map<String,Long> tickets=new HashMap<>();
    private final BrowserAuth browsers;
    private volatile ServerSocket server;
    private volatile boolean running;
    private volatile boolean terminated;
    private final Object lifecycle=new Object();
    private final Set<Socket> connections=new HashSet<>();
    private final Object credentialChanges=new Object();
    private final PairingGate pairing=new PairingGate();
    ParentServer(Context context,Store store) throws IOException {
        this.store=store;this.context=context.getApplicationContext();browsers=new BrowserAuth(context);
        try(InputStream in=context.getAssets().open("parent.html");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);page=out.toByteArray();
        }
    }
    static ParentServer active() { return active; }
    static String startupError() {return startupError;}
    boolean running() { return running; }
    boolean terminated() {return terminated;}
    static void reportFailure(IOException e) {
        startupError="管理网页启动失败，电视会自动重试。若持续失败，请重新打开应用。";
        android.util.Log.w("StudyGuard","Parent server unavailable",e);
    }
    synchronized String pair() {return offer().code;}
    synchronized PairingGate.Offer offer() {return pairing.issue(SystemClock.elapsedRealtime());}
    synchronized PairingGate.Offer connectionOffer() {if(connectionOffer==null || !pairing.valid(connectionOffer,SystemClock.elapsedRealtime()))connectionOffer=offer();return connectionOffer;}
    private PairingGate.Offer connectionOffer;
    boolean offerValid(PairingGate.Offer offer) {return pairing.valid(offer,SystemClock.elapsedRealtime());}
    void revoke() {
        synchronized(credentialChanges) {
            browsers.revokeAll();
            synchronized(this) {tickets.clear();pairing.revoke();}
        }
    }
    void start() {
        new Thread(()->{
            try (ServerSocket listener=new ServerSocket()) {
                synchronized(lifecycle) {
                    if(terminated)return;
                    server=listener;
                    listener.setReuseAddress(true);
                    listener.bind(new InetSocketAddress(PORT));
                    startupError="";
                    running=true;
                    active=this;
                }
                while(running) {
                    Socket socket=listener.accept();
                    synchronized(lifecycle) {
                        if(!running) {socket.close();break;}
                        connections.add(socket);
                        try {
                            clients.execute(()->{
                                try {handle(socket);}
                                finally {synchronized(lifecycle) {connections.remove(socket);}}
                            });
                        } catch(RejectedExecutionException e) {
                            connections.remove(socket);socket.close();
                        }
                    }
                }
            } catch(IOException e) {
                if(!terminated)reportFailure(e);
            } finally {
                stop();
            }
        },"parent-server").start();
    }
    void stop() {
        synchronized(lifecycle) {
            running=false;
            terminated=true;
            if(active==this)active=null;
            try { if(server!=null)server.close(); }catch(IOException ignored){}
            for(Socket socket:connections)try {socket.close();}catch(IOException ignored){}
            connections.clear();
            clients.shutdownNow();
        }
    }
    private synchronized String exchange(String code,String qr) {
        long now=SystemClock.elapsedRealtime();
        if(!pairing.redeem(code,qr,now))return null;
        Iterator<Map.Entry<String,Long>> it=tickets.entrySet().iterator();while(it.hasNext())if(it.next().getValue()<=now)it.remove();
        if(tickets.size()>=16)throw new IllegalArgumentException("连接过多，请稍后再试");
        String ticket=PairingGate.secret();tickets.put(ticket,now+600000);return ticket;
    }
    private synchronized void requireTicket(String ticket) {
        Long until=tickets.get(ticket);if(until==null || SystemClock.elapsedRealtime()>=until)throw new IllegalArgumentException("首次设置或恢复口令，请重新扫描电视二维码或输入电视连接码");
    }
    private JSONObject state() throws JSONException {
        JSONObject j=store.summary();
        j.put("interception",GuardService.interception()).put("storageError",store.storageError());
        j.put("overlayGranted",Ui.overlaysGranted(context)).put("hasPin",browsers.configured()).put("device",BrandGuide.name()+" · Android "+android.os.Build.VERSION.RELEASE);
        ManagedProtection.Status p=new ManagedProtection(context).status();
        return j.put("protection",new JSONObject().put("deviceOwner",p.deviceOwner).put("requested",p.requested).put("uninstallBlocked",p.uninstallBlocked).put("userControlDisabled",p.userControlDisabled).put("forceStopSupported",p.forceStopSupported).put("error",p.error));
    }
    private void handle(Socket socket) {
        try(Socket s=socket) {
            if(!s.getInetAddress().isLoopbackAddress() && !s.getInetAddress().isSiteLocalAddress()){reply(s,403,"{}",false);return;}
            s.setSoTimeout(4000);InputStream in=s.getInputStream();
            String first=line(in,2048);String[] request=first.split(" ");if(request.length!=3) { reply(s,400,"{}",false);return; }
            Map<String,String> headers=new HashMap<>();int size=0;
            while(true) { String line=line(in,4096);size+=line.length();if(size>8192) { reply(s,413,"{}",false);return; }if(line.isEmpty())break;int colon=line.indexOf(':');if(colon<=0)throw new IOException("header");String key=line.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.containsKey(key))throw new IOException("duplicate header");headers.put(key,line.substring(colon+1).trim()); }
            String origin=headers.get("origin");String host=headers.containsKey("host")?headers.get("host"):"";
            if(!host.matches("(?:[0-9.]+|localhost|\\[[0-9a-fA-F:]+\\])(?::[0-9]+)?") || (origin!=null && !origin.equals("http://"+host))) { reply(s,403,"{}",false);return; }
            if(headers.containsKey("transfer-encoding")) { reply(s,400,"{}",false);return; }
            int length=Integer.parseInt((headers.containsKey("content-length")?headers.get("content-length"):"0"));if(length<0 || length>16384) { reply(s,413,"{}",false);return; }
            byte[] body=new byte[length];int n=0;while(n<length) { int count=in.read(body,n,length-n);if(count<0)throw new EOFException();n+=count; }
            try {
            String method=request[0],path=request[1];
            if("GET".equals(method) && "/".equals(path)) { replyBytes(s,200,page,true);return; }
            if("GET".equals(method) && "/api/auth/status".equals(path)){reply(s,200,new JSONObject().put("configured",browsers.configured()).put("phraseConfigured",browsers.phraseConfigured()).toString(),false);return;}
            JSONObject data=body.length==0?new JSONObject():new JSONObject(new String(body,Ui.UTF8));
            if("POST".equals(method) && "/api/pair".equals(path)) {
                String ticket=exchange(data.optString("code"),data.has("qrToken")?data.optString("qrToken"):null);
                reply(s,ticket==null?400:200,ticket==null?"{\"error\":\"连接码已使用、已过期或尝试过多，请重新扫描电视二维码\"}":new JSONObject().put("ticket",ticket).toString(),false);return;
            }
            if("POST".equals(method) && ("/api/auth/login".equals(path)||"/api/auth/setup".equals(path)||"/api/auth/recover".equals(path))){
                JSONObject result;String pin=data.optString("pin"),confirm=data.optString("confirm"),label=data.optString("label");boolean remember=data.optBoolean("remember",false);
                if("/api/auth/login".equals(path))result=browsers.login(pin,remember,label);
                // Serialize ticket use without holding the lock the TV needs to refresh its QR code.
                else synchronized(credentialChanges){String ticket=data.optString("ticket");requireTicket(ticket);
                    if("/api/auth/setup".equals(path))result=browsers.setupWithPhrase(pin,confirm,data.optString("phrase"),remember,label);
                    else result=browsers.recoverWithPhrase(data.optString("phrase"),pin,confirm,remember,label);
                    synchronized(this) {tickets.remove(ticket);}
                }
                reply(s,200,result.toString(),false);return;
            }
            String auth=headers.containsKey("authorization")?headers.get("authorization"):"";
            String token=auth.startsWith("Bearer ")?auth.substring(7):"";
            if(!browsers.authorized(token)){reply(s,401,"{\"error\":\"请验证家长口令，浏览器授权可能已过期或被撤销\"}",false);return;}
            if("GET".equals(method) && "/api/state".equals(path)) {
                reply(s,200,state().toString(),false);
            } else if("GET".equals(method) && path.startsWith("/api/history?")) {
                android.net.Uri uri=android.net.Uri.parse(path);String kind=uri.getQueryParameter("review"),page=uri.getQueryParameter("page");
                if(!"0".equals(kind) && !"1".equals(kind))throw new IllegalArgumentException("列表类型不正确");
                if(page==null || !page.matches("[0-9]{1,9}"))throw new IllegalArgumentException("页码不正确");
                reply(s,200,store.historyPage("1".equals(kind),Integer.parseInt(page),20).toString(),false);
            } else if("PATCH".equals(method) && "/api/rules".equals(path)) {
                JSONObject patch=new JSONObject(new String(body,Ui.UTF8));if(patch.length()!=1)throw new IllegalArgumentException("每次仅修改一项设置");
                String key=patch.keys().next();Object value=patch.get(key);
                if("enabled".equals(key) && Boolean.TRUE.equals(value) && !Ui.overlaysGranted(context))throw new IllegalArgumentException("请先在电视上授权悬浮窗");
                store.updateRule(key,value);reply(s,200,state().toString(),false);
            } else if("PUT".equals(method) && "/api/rules".equals(path)) {
                cn.studyguard.core.Rules rules=Store.parseRules(new JSONObject(new String(body,Ui.UTF8)));
                if(rules.enabled && !Ui.overlaysGranted(context))throw new IllegalArgumentException("请先在电视上授权悬浮窗");
                store.updateRules(rules);reply(s,200,state().toString(),false);
            } else if("POST".equals(method) && "/api/lock".equals(path)) {
                // Phone "立即锁屏": force a pending question so the TV shows the quiz at once.
                if(!Ui.overlaysGranted(context))throw new IllegalArgumentException("请先在电视上授权悬浮窗");
                store.lockNow();
                MainActivity.remoteLock();
                reply(s,200,state().toString(),false);
            } else if("POST".equals(method) && "/api/unlock".equals(path)) {
                // Phone "解锁屏幕": protection off plus a clean pending chain.
                store.unlockNow();MainActivity.refreshAfterUnlock();reply(s,200,state().toString(),false);
            } else if("POST".equals(method) && "/api/logout".equals(path)) {
                browsers.logout(token);reply(s,200,"{}",false);
            } else if("GET".equals(method) && "/api/browsers".equals(path)){
                reply(s,200,new JSONObject().put("items",browsers.list(token)).toString(),false);
            } else if("POST".equals(method) && "/api/browsers/revoke".equals(path)){
                browsers.revoke(data.optString("id"));reply(s,200,"{}",false);
            } else if("GET".equals(method) && "/api/auth/phrase".equals(path)){
                reply(s,200,new JSONObject().put("configured",browsers.phraseConfigured()).toString(),false);
            } else if("POST".equals(method) && "/api/auth/phrase".equals(path)){
                browsers.changePhrase(data.optString("phrase"));reply(s,200,"{}",false);

            } else if("POST".equals(method) && "/api/auth/change".equals(path)){
                reply(s,200,browsers.change(data.optString("current"),data.optString("pin"),data.optString("confirm"),data.optBoolean("remember"),data.optString("label")).toString(),false);
            } else if("POST".equals(method) && "/api/protection".equals(path)){
                ManagedProtection protection=new ManagedProtection(context);String action=data.optString("action");
                if("enable".equals(action))protection.enable();else if("disable".equals(action))protection.disable();else if("release".equals(action))protection.releaseOwnership();else throw new IllegalArgumentException("未知保护操作");
                reply(s,200,state().toString(),false);
            } else reply(s,404,"{}",false);
            } catch(android.database.sqlite.SQLiteException e) {
                reply(s,503,"{\"error\":\"电视暂时无法读取学习记录，请检查存储空间后重试\"}",false);
            } catch(JSONException | IllegalArgumentException | IllegalStateException e) { reply(s,400,new JSONObject().put("error",e.getMessage()==null?"请求参数不正确":e.getMessage()).toString(),false); }
        } catch(JSONException | IllegalArgumentException | IOException ignored) { }
    }
    private static String line(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1) { if(c=='\n')return new String(out.toByteArray(),Ui.ASCII).replace("\r","");if(out.size()>=max)throw new IOException("line too long");out.write(c); }throw new EOFException();
    }
    private static void reply(Socket s,int status,String body,boolean html) throws IOException { replyBytes(s,status,body.getBytes(Ui.UTF8),html); }
    private static void replyBytes(Socket s,int status,byte[] body,boolean html) throws IOException {
        OutputStream out=s.getOutputStream();String reason=status==200?"OK":"Error";
        String headers="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+(html?"text/html":"application/json")+"; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(Ui.ASCII));out.write(body);out.flush();
    }
    static String addresses(Context context) {
        List<String> result=new ArrayList<>();
        // Prefer the active network used by the TV, then retain other LAN addresses as fallbacks.
        if(android.os.Build.VERSION.SDK_INT>=21)try { // LinkProperties network enumeration is API 21+
            android.net.ConnectivityManager manager=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network[] networks=android.os.Build.VERSION.SDK_INT>=23 && manager.getActiveNetwork()!=null
                ?new android.net.Network[]{manager.getActiveNetwork()}:manager.getAllNetworks(); // getActiveNetwork is API 23
            for(android.net.Network network:networks) {
                android.net.LinkProperties properties=manager.getLinkProperties(network);
                if(properties!=null)for(android.net.LinkAddress link:properties.getLinkAddresses()) {InetAddress a=link.getAddress();if(a instanceof Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress())result.add("http://"+a.getHostAddress()+":"+PORT);}
            }
        }catch(RuntimeException ignored){}

        try { Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()) { NetworkInterface net=interfaces.nextElement();if(!net.isUp() || net.isLoopback())continue;Enumeration<InetAddress> list=net.getInetAddresses();while(list.hasMoreElements()) { InetAddress a=list.nextElement();if(a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()){String address="http://"+a.getHostAddress()+":"+PORT;if(!result.contains(address))result.add(address);} } } }catch(Exception ignored) { }
        return result.isEmpty()?"未检测到局域网地址，请连接家庭 Wi-Fi 或网线":android.text.TextUtils.join("\n",result);
    }
}
