package ru.ladon.remote;

import android.app.*;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    static final class Action {
        final String id,title,group,type,key;
        Action(String type,String key,String title,String group){this.id=type+"."+key;this.type=type;this.key=key;this.title=title;this.group=group;}
    }
    static final ArrayList<Action> CATALOG=new ArrayList<>();
    static final String[] ICON_NAMES={"Без иконки","Звезда","Пуск","Музыка","Звук","Клавиатура","Мышь","Буфер","Интернет","Окно"};
    static final String[] ICON_VALUES={"","★","▶","♫","♪","⌨","↖","▣","●","▱"};
    static final String[] COLOR_NAMES={"Обычный","Синий","Зелёный","Оранжевый","Красный","Фиолетовый","Графитовый"};
    static final String[] COLOR_VALUES={"","#245D94","#2E7D32","#B45309","#B3261E","#6B4EFF","#455A64"};
    static final String WINDOWS_DOWNLOAD_URL="https://clickmate-site.vercel.app/#download";
    static {
        add("media","volumeup","Громче +","Медиа");add("media","volumedown","Тише −","Медиа");
        add("media","mute","Без звука","Медиа");add("media","playpause","Пуск / пауза","Медиа");
        add("media","previous","Предыдущий","Медиа");add("media","next","Следующий","Медиа");add("media","stop","Стоп","Медиа");
        String[][] keys={{"enter","Enter"},{"backspace","⌫ Стереть"},{"tab","Tab"},{"escape","Esc"},{"space","Пробел"},{"delete","Delete"},{"left","←"},{"up","↑"},{"down","↓"},{"right","→"},{"home","Home"},{"end","End"},{"pageup","Page Up"},{"pagedown","Page Down"},{"f5","F5 / обновить"},{"f11","Полный экран"}};
        for(String[] k:keys)add("key",k[0],k[1],"Клавиши");
        String[][] edit={{"copy","Копировать"},{"paste","Вставить"},{"cut","Вырезать"},{"selectall","Выделить всё"},{"undo","Отменить"},{"redo","Повторить"},{"save","Сохранить"},{"find","Найти"}};
        for(String[] k:edit)add("shortcut",k[0],k[1],"Редактирование");
        String[][] browser={{"back","Назад"},{"forward","Вперёд"},{"newtab","Новая вкладка"},{"closetab","Закрыть вкладку"},{"reopentab","Вернуть вкладку"},{"nexttab","След. вкладка"},{"prevtab","Пред. вкладка"},{"address","Адресная строка"},{"zoomin","Масштаб +"},{"zoomout","Масштаб −"},{"zoomreset","Масштаб 100%"}};
        for(String[] k:browser)add("shortcut",k[0],k[1],"Браузер");
        add("shortcut","desktop","Рабочий стол","Окна и показ");add("shortcut","switchwindow","Сменить окно","Окна и показ");add("shortcut","present","Показ с текущего","Окна и показ");
        add("widget","touchpad","Тачпад","Мышь");add("click","left","Левый щелчок","Мышь");add("click","right","Правый щелчок","Мышь");add("click","middle","Средний щелчок","Мышь");
    }
    static void add(String type,String key,String title,String group){CATALOG.add(new Action(type,key,title,group));}
    static Action find(String id){for(Action a:CATALOG)if(a.id.equals(id))return a;return null;}
    final Handler ui=new Handler(Looper.getMainLooper());
    final ThreadPoolExecutor network=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(48));
    final ExecutorService discoveryNetwork=Executors.newSingleThreadExecutor();
    volatile Socket socket;
    BufferedReader reader; BufferedWriter writer;
    volatile boolean connected=false;
    boolean connecting=false,destroyed=false;
    SharedPreferences prefs;
    int ink,muted,accent,paper,surface,padColor;
    LinearLayout root,body,tabRow,connectionActions;
    TextView connectionStatus;
    HorizontalScrollView tabs; ScrollView content;
    String current="mouse",message="Не подключено",pcName="",draft="";
    EditText text;
    final ArrayList<JSONObject> sections=new ArrayList<>();
    float lastX,lastY,startX,startY,dx,dy,scroll;
    long downAt,lastTapAt;float lastTapX,lastTapY;boolean moved,multi,dragging;
    final Runnable tick=new Runnable(){public void run(){
        if(connected){int x=Math.round(dx),y=Math.round(dy),s=(int)(scroll/18);dx-=x;dy-=y;scroll-=s*18;
            if(x!=0||y!=0)send(json("type","move","x",clamp(x,1000),"y",clamp(y,1000)),null);
            if(s!=0)send(json("type","scroll","amount",clamp((prefs.getBoolean("reverse",false)?s:-s)*120,1200)),null);
        }ui.postDelayed(this,25);
    }};
    final Runnable heartbeat=new Runnable(){public void run(){if(connected)send(json("type","ping"),null);ui.postDelayed(this,4000);}};
    @Override public void onCreate(Bundle state){
        prefs=getPreferences(0);setTheme(prefs.getBoolean("dark",false)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);
        loadSections();current=state!=null?state.getString("tab","mouse"):prefs.getString("tab","mouse");draft=state!=null?state.getString("draft",""):"";
        render();applyAwake();ui.post(tick);ui.post(heartbeat);if(state==null)handleIntent(getIntent());
    }
    @Override protected void onStart(){super.onStart();if(!connected&&!connecting){String ip=prefs.getString("known_address",""),token=prefs.getString("known_token",""),pin=prefs.getString("known_pin","");if(privateAddress(ip)&&validToken(token))resume(ip,token);else if(privateAddress(ip)&&pin.matches("[0-9]{4}"))pair(ip,pin);}}
    void render(){
        rememberDraft();boolean dark=prefs.getBoolean("dark",false);
        ink=Color.parseColor(dark?"#E6EDF5":"#20364C");muted=Color.parseColor(dark?"#A8B7CA":"#586D81");accent=Color.parseColor(dark?"#A7CDFC":"#245D94");paper=Color.parseColor(dark?"#18222E":"#EFF3F7");surface=Color.parseColor(dark?"#243343":"#FFFFFF");padColor=Color.parseColor(dark?"#2B4057":"#DCE9F5");
        getWindow().setStatusBarColor(paper);getWindow().setNavigationBarColor(paper);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(paper);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setPadding(dp(20),dp(8),dp(16),0);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("ClickMate",26);brand.setTypeface(null,Typeface.BOLD);brand.setGravity(Gravity.CENTER_VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(0,dp(52),1));header.addView(button("Настройки",v->select("settings")));root.addView(header);
        connectionStatus=label("",14);connectionStatus.setPadding(dp(22),dp(10),dp(22),dp(14));connectionStatus.setMinHeight(dp(48));connectionStatus.setOnClickListener(v->connection());connectionStatus.setFocusable(true);root.addView(connectionStatus);updateStatus();
        connectionActions=column();connectionActions.setPadding(dp(16),0,dp(16),0);row(connectionActions,new String[]{"Сканировать QR","Ввести код"},new Runnable[]{this::scan,this::connection});root.addView(connectionActions);connectionActions.setVisibility(connected?View.GONE:View.VISIBLE);
        tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);tabRow=new LinearLayout(this);tabRow.setPadding(dp(16),0,dp(16),dp(8));tabs.addView(tabRow);root.addView(tabs);
        content=new ScrollView(this);content.setFillViewport(true);body=column();body.setPadding(dp(20),dp(14),dp(20),dp(28));content.addView(body);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));showPage();
    }
    ArrayList<String> sectionOrder(){
        ArrayList<String> all=new ArrayList<>(Arrays.asList("mouse","film","keyboard","media","navigation")),ordered=new ArrayList<>();
        for(JSONObject section:sections)all.add(section.optString("id"));
        try{JSONArray saved=new JSONArray(prefs.getString("section_order","[]"));for(int i=0;i<saved.length();i++){String id=saved.optString(i);if(all.contains(id)&&!ordered.contains(id))ordered.add(id);}}catch(Exception ignored){}
        for(String id:all)if(!ordered.contains(id))ordered.add(id);return ordered;
    }
    String sectionTitle(String id){switch(id){case "mouse":return "Мышь";case "film":return "Фильм";case "keyboard":return "Клавиатура";case "media":return "Медиа";case "navigation":return "Навигация";default:JSONObject s=section(id);return s==null?"Раздел":s.optString("name");}}
    void tabs(){tabRow.removeAllViews();for(String id:sectionOrder())tab(id,sectionTitle(id));tabRow.addView(button("+ Раздел",v->editSection(null)));}
    void editSectionOrder(){
        hideKeyboard();ArrayList<String> order=sectionOrder();LinearLayout box=column();box.setPadding(dp(14),dp(6),dp(14),dp(12));
        TextView help=label("Удерживайте и перетаскивайте раздел. Или нажмите, чтобы выбрать его место.",14);help.setPadding(0,0,0,dp(12));box.addView(help);LinearLayout holder=column();box.addView(holder);sectionOrderGrid(holder,order);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Порядок разделов").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{JSONArray array=new JSONArray();for(String id:order)array.put(id);prefs.edit().putString("section_order",array.toString()).apply();tabs();}).create();showDialog(dialog);
    }
    void sectionOrderGrid(LinearLayout holder,ArrayList<String> order){
        holder.removeAllViews();TileGrid grid=new TileGrid(this,(from,to)->{order.add(to,order.remove(from));sectionOrderGrid(holder,order);});holder.addView(grid);
        for(int i=0;i<order.size();i++){final int index=i;Button tile=button(sectionTitle(order.get(i)),v->choosePosition(order.size(),index,to->{order.add(to,order.remove(index));sectionOrderGrid(holder,order);}));tile.setTextSize(13);tile.setPadding(dp(4),dp(4),dp(4),dp(4));tile.setContentDescription("Раздел: "+sectionTitle(order.get(i)));grid.tile(tile,1,88);}
    }
    interface Position {void chosen(int position);}
    void choosePosition(int count,int currentPosition,Position selected){String[] places=new String[count];for(int i=0;i<count;i++)places[i]="Место "+(i+1)+(i==currentPosition?" · сейчас":"");new AlertDialog.Builder(this).setTitle("Переместить на место").setItems(places,(d,i)->selected.chosen(i)).setNegativeButton("Отмена",null).show();}
    void hideKeyboard(){View focused=getCurrentFocus();if(focused!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}

    void tab(String id,String title){Button b=button(title,v->select(id));b.setOnLongClickListener(v->{editSectionOrder();return true;});b.setTextColor(current.equals(id)?paper:accent);b.setBackgroundTintList(ColorStateList.valueOf(current.equals(id)?accent:surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.rightMargin=dp(6);tabRow.addView(b,p);if(current.equals(id))b.post(()->tabs.smoothScrollTo(Math.max(0,b.getLeft()-dp(16)),0));}
    void select(String id){rememberDraft();current=id;prefs.edit().putString("tab",id).apply();showPage();content.scrollTo(0,0);}
    void rememberDraft(){if(text!=null)draft=text.getText().toString();text=null;}
    void showPage(){body.removeAllViews();dx=dy=scroll=0;switch(current){case "mouse":mousePage();break;case "film":filmPage();break;case "keyboard":keyboardPage();break;case "media":mediaPage();break;case "navigation":navigationPage();break;case "settings":settingsPage();break;default:JSONObject s=section(current);if(s==null){current="mouse";mousePage();}else customPage(s);}tabs();}
    void title(String heading,String subtitle){TextView t=label(heading,28);t.setTypeface(null,Typeface.BOLD);body.addView(t);note(subtitle);}
    void note(String value){TextView n=label(value,14);n.setTextColor(muted);n.setPadding(0,dp(6),0,dp(16));body.addView(n);}
    void group(String value){TextView t=label(value,18);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(18),0,dp(10));body.addView(t);}
    void mousePage(){
        title("Мышь","Касание — щелчок. Двойное касание с удержанием — перетаскивание.");TextView pad=label("＋\n\nТачпад",22);pad.setGravity(Gravity.CENTER);pad.setTextColor(accent);pad.setBackground(shape(padColor,24));pad.setContentDescription("Тачпад. Перемещение одним пальцем, прокрутка двумя, двойное касание с удержанием для перетаскивания.");
        int height=Math.max(210,Math.min(340,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*.36f)));body.addView(pad,new LinearLayout.LayoutParams(-1,dp(height)));pad.setOnTouchListener(this::touch);
        grid("click.left","click.right");row(body,new String[]{"Двойной щелчок","Средняя кнопка"},new Runnable[]{()->{if(!requireConnection())return;feedback();send(command(find("click.left")),null);send(command(find("click.left")),null);},()->act("click.middle")});
        note("Удержание пальца на месте — правый щелчок. Для перетаскивания коснитесь, затем быстро коснитесь ещё раз и ведите палец.");group("Быстрые действия");grid("shortcut.switchwindow","shortcut.desktop");
    }
    void filmPage(){
        TileGrid grid=new TileGrid(this,null);body.addView(grid);
        Button quieter=button(find("media.volumedown").title,v->act("media.volumedown"));
        Button louder=button(find("media.volumeup").title,v->act("media.volumeup"));
        Button previous=button("←",v->act("media.previous"));previous.setContentDescription(find("media.previous").title);
        Button playPause=button(find("media.playpause").title,v->act("media.playpause"));
        Button next=button("→",v->act("media.next"));next.setContentDescription(find("media.next").title);
        for(Button tile:new Button[]{quieter,louder,previous,playPause,next}){tile.setTextSize(13);tile.setPadding(dp(5),dp(4),dp(5),dp(4));}
        grid.tile(quieter,1,88);grid.tile(new View(this),1,88);grid.tile(louder,1,88);
        grid.tile(previous,1,88);grid.tile(playPause,1,88);grid.tile(next,1,88);
        grid.tile(touchpad(),3,220);
    }
    void keyboardPage(){
        title("Клавиатура","Текст отправляется в активное окно на ПК.");text=field("Введите текст…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,1000);text.setMinLines(2);text.setText(draft);body.addView(text);
        body.addView(primary("Ввести текст",v->{if(!requireConnection())return;final String value=text.getText().toString();if(value.isEmpty())return;feedback();send(json("type","text","text",value),()->{if(text!=null&&text.getText().toString().equals(value))text.setText("");if(draft.equals(value))draft="";});}));
        group("Клавиши");grid("key.escape","key.tab","key.backspace","key.enter","key.space","key.delete");row(body,new String[]{"←","↑","↓","→"},new Runnable[]{()->act("key.left"),()->act("key.up"),()->act("key.down"),()->act("key.right")});
        group("Редактирование");grid("shortcut.copy","shortcut.paste","shortcut.cut","shortcut.selectall","shortcut.undo","shortcut.redo","shortcut.save","shortcut.find");
    }
    void mediaPage(){
        title("Медиа","Музыка, видео и звук компьютера.");group("Воспроизведение");body.addView(primary("▶  /  Ⅱ     Пуск / пауза",v->act("media.playpause")),new LinearLayout.LayoutParams(-1,dp(88)));grid("media.previous","media.next");
        group("Громкость");grid("media.volumedown","media.volumeup","media.mute","media.stop");note("Команды получает активный медиаплеер Windows. Кнопка «Без звука» также возвращает звук.");
        group("Видео в активном окне");grid("key.left","key.right","key.f11","key.escape");note("Стрелки и полный экран зависят от выбранного плеера.");
    }
    void navigationPage(){
        title("Навигация","Браузер, окна и презентации.");group("Браузер");grid("shortcut.back","shortcut.forward","shortcut.newtab","shortcut.closetab","shortcut.prevtab","shortcut.nexttab","shortcut.reopentab","shortcut.address","key.f5","key.f11");
        group("Масштаб страницы");grid("shortcut.zoomout","shortcut.zoomin","shortcut.zoomreset");group("Презентация");grid("key.f5","shortcut.present","key.pageup","key.pagedown","key.escape");note("F5 — показ с начала; Shift+F5 — с текущего слайда. Сначала выберите окно презентации на ПК.");group("Окна");grid("shortcut.switchwindow","shortcut.desktop","key.home","key.end");
    }
    void settingsPage(){
        title("Настройки","Подстройте пульт под себя. Изменения сохраняются сразу.");group("Подключение");body.addView(button(connected?"Подключено к "+pcName:"Подключить компьютер",v->connection()));group("Тачпад");
        slider("Чувствительность мыши","sensitivity",1f,.5f,3f);slider("Скорость прокрутки","scrollSpeed",1f,.5f,2.5f);toggle("Щелчок касанием","tap",true,null);toggle("Обратная прокрутка","reverse",false,null);
        group("Приложение");toggle("Отклик при нажатии","haptic",true,null);toggle("Не гасить экран при подключении","awake",true,this::applyAwake);
        toggle("Тёмная тема","dark",false,()->{int position=content.getScrollY();setTheme(prefs.getBoolean("dark",false)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);render();content.post(()->content.scrollTo(0,position));});
        group("Свои разделы");body.addView(button("Порядок разделов",v->editSectionOrder()));body.addView(primary("+ Создать раздел",v->editSection(null)));for(JSONObject s:sections)sectionSettingsRow(s);
        group("ClickMate 0.12.0");note("Четырёхзначный код нужен только при первом знакомстве. Доверенный телефон подключается автоматически даже после перезапуска ПК.");
    }
    void sectionSettingsRow(JSONObject section){
        String name=section.optString("name");
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(name,15);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0,0,dp(6),0);row.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button edit=button("Изменить",v->editSection(section)),remove=button("Удалить",v->deleteSection(section));
        edit.setContentDescription(name+" · изменить");remove.setContentDescription(name+" · удалить");
        for(Button action:new Button[]{edit,remove}){action.setTextSize(12);action.setMinWidth(0);action.setMinimumWidth(0);action.setPadding(dp(6),0,dp(6),0);row.addView(action,new LinearLayout.LayoutParams(-2,dp(52)));}
        body.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    void slider(String name,String key,float def,float min,float max){float value=prefs.getFloat(key,def);TextView label=label(name+" · "+String.format(Locale.getDefault(),"%.1f×",value),15);body.addView(label);SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(Math.round((value-min)/(max-min)*100));seek.setProgressTintList(ColorStateList.valueOf(accent));seek.setThumbTintList(ColorStateList.valueOf(accent));seek.setContentDescription(name);body.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){float v=min+(max-min)*p/100;label.setText(name+" · "+String.format(Locale.getDefault(),"%.1f×",v));if(user)prefs.edit().putFloat(key,v).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}
    void toggle(String title,String key,boolean def,Runnable after){Switch s=new Switch(this);s.setText(title);s.setTextSize(15);s.setTextColor(ink);s.setMinHeight(dp(56));s.setChecked(prefs.getBoolean(key,def));body.addView(s);s.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();if(after!=null)after.run();});}
    void applyAwake(){if(connected&&prefs.getBoolean("awake",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}

    boolean isTouchpad(JSONObject b){return b!=null&&"widget.touchpad".equals(b.optString("action"));}
    boolean allowed(String value,String[] choices){for(String choice:choices)if(choice.equals(value))return true;return false;}
    int buttonSpan(JSONObject b){if(isTouchpad(b))return 3;int span=b==null?1:b.optInt("span",1);return span>=1&&span<=3?span:1;}
    String buttonIcon(JSONObject b){String value=b==null?"":b.optString("icon","");return allowed(value,ICON_VALUES)?value:"";}
    String buttonColor(JSONObject b){String value=b==null?"":b.optString("color","");return allowed(value,COLOR_VALUES)?value:"";}
    String styledTitle(JSONObject b){String icon=buttonIcon(b),title=buttonTitle(b);return icon.isEmpty()?title:icon+"  "+title;}
    void applyTileStyle(Button tile,JSONObject spec){String color=buttonColor(spec);if(!color.isEmpty()){tile.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));tile.setTextColor(Color.WHITE);}}
    void copyAppearance(JSONObject from,JSONObject to){try{int span=buttonSpan(from);String icon=buttonIcon(from),color=buttonColor(from);if(span>1)to.put("span",span);if(!icon.isEmpty())to.put("icon",icon);if(!color.isEmpty())to.put("color",color);}catch(JSONException ex){throw new IllegalArgumentException(ex);}}
    JSONObject sanitizedButton(JSONObject b){if(b==null)return null;if(isEmpty(b))return json("empty",true);JSONObject clean;if(b.has("text")&&b.optString("text").length()<=1000&&!b.optString("label").trim().isEmpty()&&b.optString("label").length()<=30)clean=json("label",b.optString("label"),"text",b.optString("text"));else if(find(b.optString("action"))!=null)clean=json("action",b.optString("action"));else return null;copyAppearance(b,clean);return clean;}
    TextView touchpad(){TextView pad=label("＋\n\nТачпад",21);pad.setGravity(Gravity.CENTER);pad.setTextColor(accent);pad.setBackground(shape(padColor,20));pad.setContentDescription("Тачпад. Перемещение одним пальцем, прокрутка двумя.");pad.setOnTouchListener(this::touch);return pad;}
    void customPage(JSONObject s){
        JSONArray buttons=s.optJSONArray("buttons");
        if(buttons==null||buttons.length()==0)note("Здесь пока нет кнопок. Откройте настройки, чтобы добавить команды, тачпад или свой текст.");
        else {TileGrid grid=new TileGrid(this,null);body.addView(grid);for(int i=0;i<buttons.length();i++){final JSONObject spec=buttons.optJSONObject(i);if(isEmpty(spec))grid.tile(new View(this),1,88);else if(isTouchpad(spec))grid.tile(touchpad(),3,220);else{Button tile=button(styledTitle(spec),v->customAction(spec));tile.setTextSize(13);tile.setPadding(dp(5),dp(4),dp(5),dp(4));applyTileStyle(tile,spec);grid.tile(tile,buttonSpan(spec),88);}}}

    }

    void deleteSection(JSONObject s){new AlertDialog.Builder(this).setTitle("Удалить «"+s.optString("name")+"»?").setMessage("Будет удалён только этот раздел и его кнопки.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{sections.remove(s);saveSections();showPage();}).show();}

    String buttonTitle(JSONObject b){if(b==null)return "Кнопка";if(b.has("text"))return b.optString("label","Текст");Action a=find(b.optString("action"));return a==null?"Недоступная команда":a.title;}
    void customAction(JSONObject b){if(b==null)return;if(b.has("text")){if(!requireConnection())return;feedback();send(json("type","text","text",b.optString("text")),null);}else act(b.optString("action"));}
    JSONObject section(String id){for(JSONObject s:sections)if(s.optString("id").equals(id))return s;return null;}
    void saveSections(){JSONArray array=new JSONArray();for(JSONObject s:sections)array.put(s);prefs.edit().putString("sections",array.toString()).apply();}
    void loadSections(){try{JSONArray saved=new JSONArray(prefs.getString("sections","[]"));Set<String> ids=new HashSet<>();for(int i=0;i<Math.min(saved.length(),12);i++){JSONObject s=saved.optJSONObject(i);if(s==null)continue;String id=s.optString("id"),name=s.optString("name");if(!id.startsWith("custom-")||!ids.add(id)||name.trim().isEmpty()||name.length()>30)continue;JSONArray clean=new JSONArray(),buttons=s.optJSONArray("buttons");if(buttons!=null)for(int j=0;j<Math.min(buttons.length(),96);j++){JSONObject button=sanitizedButton(buttons.optJSONObject(j));if(button!=null)clean.put(button);}sections.add(json("id",id,"name",name,"buttons",clean));}}catch(Exception ex){toast("Не удалось прочитать сохранённые разделы");}}
    void editSection(JSONObject original){
        hideKeyboard();
        if(original==null&&sections.size()>=12){toast("Можно создать до 12 своих разделов");return;}
        LinearLayout box=column();box.setPadding(dp(18),dp(4),dp(18),dp(8));EditText name=field("Название раздела",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,30);if(original!=null)name.setText(original.optString("name"));box.addView(name);
        TextView tip=label("Нажмите кнопку, чтобы выбрать размер, иконку и цвет. Удерживайте для перемещения.",13);tip.setTextColor(muted);tip.setPadding(0,dp(10),0,dp(10));box.addView(tip);LinearLayout selected=column();box.addView(selected);
        ArrayList<JSONObject> buttons=new ArrayList<>();if(original!=null){JSONArray array=original.optJSONArray("buttons");if(array!=null)for(int i=0;i<array.length();i++)buttons.add(array.optJSONObject(i));}Runnable redraw=()->editorRows(selected,buttons);redraw.run();
        box.addView(button("+ Добавить команду",v->{if(buttonCount(buttons)>=24){toast("Максимум 24 кнопки");return;}pickAction(a->{addTile(buttons,json("action",a.id));redraw.run();});}));
        box.addView(button("+ Кнопка со своим текстом",v->{if(buttonCount(buttons)>=24){toast("Максимум 24 кнопки");return;}textButtonEditor(null,b->{addTile(buttons,b);redraw.run();});}));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"Новый раздел":"Изменить раздел").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Введите название");return;}for(JSONObject s:sections)if(s!=original&&s.optString("name").equalsIgnoreCase(title)){name.setError("Раздел с таким названием уже есть");return;}trimEmpty(buttons);JSONArray array=new JSONArray();for(JSONObject b:buttons)array.put(b);String id=original==null?"custom-"+UUID.randomUUID():original.optString("id");JSONObject saved=json("id",id,"name",title,"buttons",array);if(original==null)sections.add(saved);else sections.set(sections.indexOf(original),saved);saveSections();dialog.dismiss();select(id);}));showDialog(dialog);
    }
    boolean isEmpty(JSONObject b){return b!=null&&b.optBoolean("empty",false);}
    int buttonCount(ArrayList<JSONObject> buttons){int n=0;for(JSONObject b:buttons)if(!isEmpty(b))n++;return n;}
    void trimEmpty(ArrayList<JSONObject> buttons){while(!buttons.isEmpty()&&isEmpty(buttons.get(buttons.size()-1)))buttons.remove(buttons.size()-1);}
    void addTile(ArrayList<JSONObject> buttons,JSONObject b){for(int i=0;i<buttons.size();i++)if(isEmpty(buttons.get(i))){buttons.set(i,b);return;}if(buttons.size()<96)buttons.add(b);else toast("Нет свободных ячеек");}
    void prepareCells(ArrayList<JSONObject> buttons){
        trimEmpty(buttons);int column=0;
        for(int i=0;i<buttons.size();i++){int span=buttonSpan(buttons.get(i));if(column+span>3){while(column!=0&&buttons.size()<96){buttons.add(i++,json("empty",true));column=(column+1)%3;}}column=(column+span)%3;}
        int extra=(3-column)%3+3;for(int i=0;i<extra&&buttons.size()<96;i++)buttons.add(json("empty",true));
    }
    void editorRows(LinearLayout parent,ArrayList<JSONObject> buttons){
        prepareCells(buttons);parent.removeAllViews();TileGrid grid=new TileGrid(this,(from,to)->{Collections.swap(buttons,from,to);editorRows(parent,buttons);});parent.addView(grid);
        for(int i=0;i<buttons.size();i++){
            final int index=i;JSONObject spec=buttons.get(i);
            if(isEmpty(spec)){Button empty=button("＋",v->{if(buttonCount(buttons)>=24){toast("Максимум 24 кнопки");return;}pickAction(a->{buttons.set(index,json("action",a.id));editorRows(parent,buttons);});});empty.setAlpha(.35f);empty.setContentDescription("Пустая ячейка "+(i+1));grid.tile(empty,1,88);empty.setOnLongClickListener(null);continue;}
            Button tile=button(styledTitle(spec)+(isTouchpad(spec)?"\nНа всю ширину":""),v->{
                hideKeyboard();String[] actions=isTouchpad(spec)?new String[]{"Переместить","Удалить кнопку"}:spec.has("text")?new String[]{"Изменить текст","Размер, иконка и цвет","Переместить","Удалить кнопку"}:new String[]{"Размер, иконка и цвет","Переместить","Удалить кнопку"};
                new AlertDialog.Builder(this).setTitle(buttonTitle(spec)).setItems(actions,(d,which)->{
                    String action=actions[which];if(action.equals("Изменить текст"))textButtonEditor(spec,b->{buttons.set(index,b);editorRows(parent,buttons);});
                    else if(action.equals("Размер, иконка и цвет"))styleButton(spec,b->{buttons.set(index,b);editorRows(parent,buttons);});
                    else if(action.equals("Переместить"))choosePosition(buttons.size(),index,to->{Collections.swap(buttons,index,to);editorRows(parent,buttons);});
                    else {buttons.set(index,json("empty",true));editorRows(parent,buttons);}
                }).setNegativeButton("Отмена",null).show();
            });
            tile.setTextSize(13);tile.setPadding(dp(4),dp(4),dp(4),dp(4));tile.setContentDescription("Кнопка "+(i+1)+": "+buttonTitle(spec));if(isTouchpad(spec))tile.setBackgroundTintList(ColorStateList.valueOf(padColor));else applyTileStyle(tile,spec);grid.tile(tile,buttonSpan(spec),isTouchpad(spec)?128:88);
        }
    }

    interface ChosenAction {void accept(Action action);} interface ChosenButton {void accept(JSONObject button);}
    void pickAction(ChosenAction callback){hideKeyboard();String[] groups={"Медиа","Клавиши","Редактирование","Браузер","Окна и показ","Мышь"};new AlertDialog.Builder(this).setTitle("Какие кнопки добавить?").setItems(groups,(d,which)->{ArrayList<Action> actions=new ArrayList<>();for(Action a:CATALOG)if(a.group.equals(groups[which]))actions.add(a);String[] names=new String[actions.size()];for(int i=0;i<names.length;i++)names[i]=actions.get(i).title;new AlertDialog.Builder(this).setTitle(groups[which]).setItems(names,(dialog,index)->callback.accept(actions.get(index))).setNegativeButton("Отмена",null).show();}).setNegativeButton("Отмена",null).show();}
    void textButtonEditor(JSONObject existing,ChosenButton callback){LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);EditText title=field("Название кнопки",InputType.TYPE_CLASS_TEXT,30),value=field("Текст для ввода на ПК",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,1000);value.setMinLines(3);if(existing!=null){title.setText(existing.optString("label"));value.setText(existing.optString("text"));}box.addView(title);box.addView(value);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Кнопка с текстом").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Готово",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String label=title.getText().toString().trim(),s=value.getText().toString();if(label.isEmpty()){title.setError("Введите название");return;}if(s.isEmpty()){value.setError("Введите текст");return;}JSONObject result=json("label",label,"text",s);if(existing!=null)copyAppearance(existing,result);callback.accept(result);dialog.dismiss();}));showDialog(dialog);}
    void styleButton(JSONObject existing,ChosenButton callback){
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);String[] sizes={"1 ячейка","2 ячейки","3 ячейки"};Spinner size=new Spinner(this),icon=new Spinner(this),color=new Spinner(this);size.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,sizes));icon.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,ICON_NAMES));color.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,COLOR_NAMES));size.setSelection(buttonSpan(existing)-1);icon.setSelection(indexOf(ICON_VALUES,buttonIcon(existing)));color.setSelection(indexOf(COLOR_VALUES,buttonColor(existing)));size.setContentDescription("Размер кнопки");icon.setContentDescription("Иконка кнопки");color.setContentDescription("Цвет кнопки");box.addView(label("Размер",13));box.addView(size);box.addView(label("Иконка",13));box.addView(icon);box.addView(label("Цвет",13));box.addView(color);
        new AlertDialog.Builder(this).setTitle(buttonTitle(existing)).setView(box).setNegativeButton("Отмена",null).setPositiveButton("Готово",(d,w)->{try{JSONObject result=new JSONObject(existing.toString());result.remove("span");result.remove("icon");result.remove("color");if(size.getSelectedItemPosition()>0)result.put("span",size.getSelectedItemPosition()+1);String selectedIcon=ICON_VALUES[icon.getSelectedItemPosition()],selectedColor=COLOR_VALUES[color.getSelectedItemPosition()];if(!selectedIcon.isEmpty())result.put("icon",selectedIcon);if(!selectedColor.isEmpty())result.put("color",selectedColor);callback.accept(result);}catch(JSONException ex){toast("Не удалось сохранить оформление");}}).show();
    }
    int indexOf(String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;return 0;}

    void scan(){startActivityForResult(new android.content.Intent(this,QrScannerActivity.class),30);}
    void handleIntent(android.content.Intent intent){if(intent!=null&&android.content.Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null){String value=intent.getData().toString();intent.setData(null);openPairing(value);}}
    void openPairing(String value){PairingLink link=PairingLink.parse(value);if(link==null){toast("Это не код подключения ClickMate");return;}disconnect("Подключение по QR");pair(link.address,link.pin);}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==30&&result==RESULT_OK&&data!=null)openPairing(data.getStringExtra("pairing"));}
    void connection(){
        if(connecting){new AlertDialog.Builder(this).setTitle("Подключение…").setMessage("Ожидаем ответ компьютера").setNegativeButton("Закрыть",null).setPositiveButton("Отменить подключение",(d,w)->disconnect("Подключение отменено")).show();return;}
        if(connected){new AlertDialog.Builder(this).setTitle(pcName).setMessage("Подключено · "+prefs.getString("address","")).setNegativeButton("Оставить",null).setPositiveButton("Отключиться",(d,w)->disconnect("Отключено")).show();return;}
        LinearLayout box=column();box.setPadding(dp(20),dp(4),dp(20),0);box.addView(label("Доступные компьютеры в этой сети",14));
        TextView discoveryStatus=label("Ищем компьютеры…",13);discoveryStatus.setTextColor(muted);discoveryStatus.setPadding(0,dp(8),0,dp(4));box.addView(discoveryStatus);LinearLayout found=column();box.addView(found);
        EditText address=field("IP-адрес компьютера",InputType.TYPE_CLASS_PHONE,15);String savedAddress=prefs.getString("known_address",prefs.getString("address",""));address.setText(savedAddress);EditText pin=field("Код из 4 цифр",InputType.TYPE_CLASS_NUMBER,4);if(savedAddress.equals(prefs.getString("known_address","")))pin.setText(prefs.getString("known_pin",""));
        Button refresh=button("Обновить список",v->discover(found,discoveryStatus,address,pin));box.addView(refresh);TextView manual=label("Или подключитесь вручную",13);manual.setTextColor(muted);manual.setPadding(0,dp(12),0,0);box.addView(manual);box.addView(address);box.addView(pin);
        TextView downloadPc=label("Нет программы на компьютере? Скачать ClickMate для Windows",14);downloadPc.setTextColor(accent);downloadPc.setGravity(Gravity.CENTER);downloadPc.setPadding(dp(8),dp(16),dp(8),dp(8));downloadPc.setClickable(true);downloadPc.setFocusable(true);downloadPc.setOnClickListener(v->{try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(WINDOWS_DOWNLOAD_URL)));}catch(Exception ex){toast("Не удалось открыть страницу загрузки");}});box.addView(downloadPc);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Подключить ПК").setView(box).setNeutralButton("Сканировать QR",(d,w)->scan()).setNegativeButton("Отмена",null).setPositiveButton("Подключиться",null).create();dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ip=address.getText().toString().trim(),code=pin.getText().toString(),token=prefs.getString("known_token","");if(!privateAddress(ip)){address.setError("Выберите найденный ПК или введите локальный IPv4");return;}if(ip.equals(pin.getTag())&&validToken(token)){dialog.dismiss();resume(ip,token);return;}if(!code.matches("[0-9]{4}")){pin.setError("Введите 4 цифры");return;}dialog.dismiss();pair(ip,code);});discover(found,discoveryStatus,address,pin);});showDialog(dialog);
    }
    void discover(LinearLayout found,TextView status,EditText address,EditText pin){
        found.removeAllViews();status.setText("Ищем компьютеры…");discoveryNetwork.execute(()->{
            LinkedHashMap<String,String> pcs=new LinkedHashMap<>();
            try(DatagramSocket socket=new DatagramSocket()){
                socket.setBroadcast(true);socket.setSoTimeout(250);byte[] request="LADON_DISCOVER_V1".getBytes(StandardCharsets.US_ASCII);LinkedHashSet<InetAddress> targets=new LinkedHashSet<>();targets.add(InetAddress.getByName("255.255.255.255"));
                Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface networkInterface=interfaces.nextElement();if(!networkInterface.isUp()||networkInterface.isLoopback())continue;for(InterfaceAddress item:networkInterface.getInterfaceAddresses())if(item.getBroadcast()!=null)targets.add(item.getBroadcast());}
                for(InetAddress target:targets)socket.send(new DatagramPacket(request,request.length,target,48733));long until=SystemClock.elapsedRealtime()+1700;
                while(SystemClock.elapsedRealtime()<until){try{byte[] data=new byte[1024];DatagramPacket packet=new DatagramPacket(data,data.length);socket.receive(packet);String ip=packet.getAddress().getHostAddress();if(!privateAddress(ip)||pcs.containsKey(ip))continue;JSONObject reply=new JSONObject(new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8));if(!"ladon-pc".equals(reply.optString("type"))||reply.optInt("port")!=48732)continue;String name=reply.optString("name","ПК").trim();if(name.isEmpty()||name.length()>80)name="ПК";pcs.put(ip,name);}catch(SocketTimeoutException ignored){}}
            }catch(Exception ignored){}
            ui.post(()->{if(destroyed||!status.isAttachedToWindow())return;found.removeAllViews();if(pcs.isEmpty()){status.setText("ПК не найдены. Можно обновить список или ввести адрес.");return;}status.setText("Найдено: "+pcs.size());for(Map.Entry<String,String> pc:pcs.entrySet()){final String ip=pc.getKey(),name=pc.getValue();found.addView(button(name+"  ·  "+ip,v->{address.setText(ip);String token=prefs.getString("known_token","");if(name.equals(prefs.getString("known_name",""))&&validToken(token)){pin.setText("");pin.setTag(ip);toast("Доверенный ПК — код не требуется");}else{pin.setTag(null);pin.setText("");pin.requestFocus();toast("Введите код с экрана ПК");}}));}});
        });
    }
    void showDialog(AlertDialog dialog){dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();}
    void updateStatus(){if(connectionActions!=null)connectionActions.setVisibility(connected?View.GONE:View.VISIBLE);if(connectionStatus!=null){connectionStatus.setText(connected?"●  "+pcName+" · подключено":connecting?"◌  Подключение…":"○  "+message+" · подключить");connectionStatus.setTextColor(connected?accent:muted);}}
    boolean requireConnection(){if(connected)return true;if(connecting)toast("Подождите подключения");else connection();return false;}
    void act(String id){Action a=find(id);if(a==null||!requireConnection())return;feedback();send(command(a),null);}
    JSONObject command(Action a){return json("type",a.type,a.type.equals("click")?"button":"key",a.key);}
    void feedback(){if(root!=null&&prefs.getBoolean("haptic",true))root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    void grid(String... ids){for(int i=0;i<ids.length;i+=2){final String first=ids[i];if(i+1<ids.length){final String second=ids[i+1];row(body,new String[]{find(first).title,find(second).title},new Runnable[]{()->act(first),()->act(second)});}else body.addView(button(find(first).title,v->act(first)));}}
    void row(LinearLayout parent,String[] names,Runnable[] actions){LinearLayout row=new LinearLayout(this);for(int i=0;i<names.length;i++){final Runnable a=actions[i];LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(button(names[i],v->a.run()),p);}parent.addView(row);}
    Button small(String text,String description,Runnable action){Button b=button(text,v->action.run());b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setContentDescription(description);return b;}
    Button button(String title,View.OnClickListener action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(accent);b.setMinHeight(dp(48));b.setBackgroundTintList(ColorStateList.valueOf(surface));b.setOnClickListener(action);return b;}
    Button primary(String title,View.OnClickListener action){Button b=button(title,action);b.setTextColor(paper);b.setBackgroundTintList(ColorStateList.valueOf(accent));return b;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(ink);return v;}
    EditText field(String hint,int type,int max){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setInputType(type);e.setTextColor(ink);e.setHintTextColor(muted);e.setBackgroundTintList(ColorStateList.valueOf(accent));e.setPadding(dp(10),dp(12),dp(10),dp(12));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});return e;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}int clamp(int n,int limit){return Math.max(-limit,Math.min(limit,n));}void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    JSONObject json(Object... values){JSONObject j=new JSONObject();try{for(int i=0;i<values.length;i+=2)j.put((String)values[i],values[i+1]);}catch(Exception ex){throw new IllegalArgumentException(ex);}return j;}
    boolean touch(View v,MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(!connected){requireConnection();return true;}v.getParent().requestDisallowInterceptTouchEvent(true);lastX=startX=e.getX();lastY=startY=e.getY();downAt=System.currentTimeMillis();moved=false;multi=false;dragging=downAt-lastTapAt<360&&Math.abs(startX-lastTapX)+Math.abs(startY-lastTapY)<dp(48);if(dragging){lastTapAt=0;feedback();send(json("type","button","button","left","state","down"),null);}}else if(action==MotionEvent.ACTION_POINTER_DOWN){multi=true;lastTapAt=0;lastY=e.getY();if(dragging){dragging=false;send(json("type","button","button","left","state","up"),null);}}else if(action==MotionEvent.ACTION_MOVE){if(e.getPointerCount()>1){scroll+=(e.getY()-lastY)*prefs.getFloat("scrollSpeed",1f);lastY=e.getY();}else if(!multi){float x=e.getX(),y=e.getY(),speed=prefs.getFloat("sensitivity",1f);dx+=(x-lastX)*speed;dy+=(y-lastY)*speed;lastX=x;lastY=y;if(Math.abs(x-startX)+Math.abs(y-startY)>dp(7))moved=true;}}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){v.getParent().requestDisallowInterceptTouchEvent(false);if(dragging){dragging=false;send(json("type","button","button","left","state","up"),null);}else if(connected&&action==MotionEvent.ACTION_UP&&!moved&&!multi){long held=System.currentTimeMillis()-downAt;if(held>500){lastTapAt=0;v.performClick();act("click.right");}else if(held<300&&prefs.getBoolean("tap",true)){lastTapAt=System.currentTimeMillis();lastTapX=e.getX();lastTapY=e.getY();v.performClick();act("click.left");}}else lastTapAt=0;}return true;}
    boolean privateAddress(String value){return PairingLink.isPrivateAddress(value);}
    boolean validToken(String token){return token!=null&&token.length()>=40&&token.length()<=128;}
    String trustedProof(String token,String challenge)throws Exception{byte[] key=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return Base64.getEncoder().encodeToString(mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8)));}
    void pair(String ip,String code){connectPc(ip,code,null);}
    void resume(String ip,String token){connectPc(ip,null,token);}
    void connectPc(String ip,String code,String trustedToken){connecting=true;message="Подключение…";updateStatus();final boolean trusted=validToken(trustedToken);final Socket next=new Socket();socket=next;enqueue(()->{try{next.connect(new InetSocketAddress(ip,48732),5000);next.setSoTimeout(5000);next.setTcpNoDelay(true);reader=new BufferedReader(new InputStreamReader(next.getInputStream(),StandardCharsets.UTF_8));writer=new BufferedWriter(new OutputStreamWriter(next.getOutputStream(),StandardCharsets.UTF_8));JSONObject response;if(trusted){JSONObject challenge=exchange(json("type","resume"));String nonce=challenge.optString("challenge","");if(!challenge.optBoolean("ok")||nonce.length()<20)throw new IOException("Обновите программу ClickMate на ПК до версии 0.9.0");response=exchange(json("type","resume_proof","proof",trustedProof(trustedToken,nonce)));}else response=exchange(json("type","pair","pin",code));if(!response.optBoolean("ok")){if(trusted)prefs.edit().remove("known_token").apply();throw new IOException(response.optString("error","Подключение отклонено"));}if(response.optInt("protocol",1)<4)throw new IOException("Обновите программу ClickMate на ПК до версии 0.9.0");String issued=response.optString("token","");if(!trusted&&!validToken(issued))throw new IOException("ПК не выдал ключ доверия. Обновите ClickMate на ПК");ui.post(()->{if(socket!=next||destroyed)return;connecting=false;connected=true;pcName=response.optString("name","ПК");SharedPreferences.Editor edit=prefs.edit().putString("address",ip).putString("known_address",ip).putString("known_name",pcName);if(!trusted)edit.putString("known_token",issued).remove("known_pin");edit.apply();updateStatus();applyAwake();if(current.equals("settings"))showPage();});}catch(Exception ex){fail(next,friendly(ex));}});}
    String friendly(Exception ex){if(ex instanceof SocketTimeoutException)return "ПК не отвечает. Проверьте Wi-Fi и брандмауэр";if(ex instanceof ConnectException)return "Откройте программу на ПК и проверьте адрес";return ex.getMessage()==null?"Проверьте сеть и повторите":ex.getMessage();}
    JSONObject exchange(JSONObject command)throws Exception{writer.write(command.toString());writer.write('\n');writer.flush();StringBuilder line=new StringBuilder();int ch;while((ch=reader.read())!=-1&&ch!='\n'){if(line.length()>=8192)throw new IOException("Некорректный ответ ПК");line.append((char)ch);}if(ch==-1)throw new IOException("ПК отключился");return new JSONObject(line.toString());}
    void send(JSONObject command,Runnable onSuccess){if(!connected)return;final Socket next=socket;enqueue(()->{if(!connected||next!=socket)return;try{JSONObject r=exchange(command);ui.post(()->{if(socket!=next||destroyed)return;if(!r.optBoolean("ok"))toast(r.optString("error","Команда не выполнена"));else if(onSuccess!=null)onSuccess.run();});}catch(Exception ex){fail(next,"Связь прервана");}});}
    void enqueue(Runnable work){try{network.execute(work);}catch(RejectedExecutionException ex){disconnect("Соединение не успевает. Подключитесь снова");}}
    void fail(Socket next,String error){ui.post(()->{if(socket==next&&!destroyed){disconnect(error);toast(error);}});}
    void disconnect(String reason){connected=false;connecting=false;dragging=false;lastTapAt=0;Socket old=socket;socket=null;if(old!=null)try{old.close();}catch(IOException ignored){}network.getQueue().clear();dx=dy=scroll=0;message=reason;updateStatus();applyAwake();if(current.equals("settings")&&body!=null&&!destroyed)showPage();}
    @Override protected void onSaveInstanceState(Bundle out){if(text!=null)draft=text.getText().toString();out.putString("tab",current);out.putString("draft",draft);super.onSaveInstanceState(out);}
    @Override protected void onStop(){super.onStop();disconnect("Не подключено");}
    @Override protected void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);network.shutdownNow();discoveryNetwork.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(!current.equals("mouse")){select("mouse");return;}super.onBackPressed();}
}
