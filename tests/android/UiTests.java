package ru.ladon.remote.tests;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.os.SystemClock;
import android.graphics.Rect;
import java.util.*;

public class UiTests extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    void report(String value){Bundle b=new Bundle();b.putString("stream","PASS: "+value+"\n");sendStatus(0,b);}
    ArrayList<AccessibilityNodeInfo> nodes(){ArrayList<AccessibilityNodeInfo> result=new ArrayList<>();walk(getUiAutomation().getRootInActiveWindow(),result);return result;}
    void walk(AccessibilityNodeInfo n,ArrayList<AccessibilityNodeInfo> list){if(n==null)return;list.add(n);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),list);}
    void settle(){try{getUiAutomation().waitForIdle(200,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    AccessibilityNodeInfo find(String text,boolean clickable){
        long until=System.currentTimeMillis()+4000;
        while(System.currentTimeMillis()<until){for(AccessibilityNodeInfo n:nodes())if(n.isVisibleToUser()&&((n.getText()!=null&&n.getText().toString().equalsIgnoreCase(text))||(n.getContentDescription()!=null&&n.getContentDescription().toString().equals(text)))&&(!clickable||n.isClickable()))return n;try{Thread.sleep(100);}catch(Exception e){}}
        throw new AssertionError("Not found: "+text);
    }
    void tap(String text){if(!find(text,true).performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot click "+text);settle();}
    void exists(String text){find(text,false);report(text);}
    void field(int index,String text){long until=System.currentTimeMillis()+4000;while(System.currentTimeMillis()<until){ArrayList<AccessibilityNodeInfo> fields=new ArrayList<>();for(AccessibilityNodeInfo n:nodes())if("android.widget.EditText".contentEquals(n.getClassName()))fields.add(n);Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);if(fields.size()>index){fields.get(index).performAction(AccessibilityNodeInfo.ACTION_FOCUS);if(fields.get(index).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value)){settle();return;}}try{Thread.sleep(150);}catch(Exception ignored){}}throw new AssertionError("Cannot set field "+index);}
    void scrollTo(String text){SystemClock.sleep(400);for(int i=0;i<10;i++){for(AccessibilityNodeInfo n:nodes())if(((n.getText()!=null&&text.equalsIgnoreCase(n.getText().toString()))||(n.getContentDescription()!=null&&text.equals(n.getContentDescription().toString())))&&n.isVisibleToUser())return;boolean scrolled=false;for(AccessibilityNodeInfo n:nodes())if("android.widget.ScrollView".contentEquals(n.getClassName())&&n.isScrollable()){scrolled=n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);break;}settle();SystemClock.sleep(250);}find(text,false);}
    void screenshot(String name){try{android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),name+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}catch(Exception ex){throw new RuntimeException(ex);}}
    void motion(long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);if(!getUiAutomation().injectInputEvent(e,true))throw new AssertionError("Touch injection failed");e.recycle();}
    void drag(String from,String to)throws Exception{
        Thread.sleep(500);Rect a=new Rect(),b=new Rect();find(from,true).getBoundsInScreen(a);find(to,true).getBoundsInScreen(b);long down=SystemClock.uptimeMillis();motion(down,MotionEvent.ACTION_DOWN,a.centerX(),a.centerY());Thread.sleep(800);
        for(int i=1;i<=16;i++){motion(down,MotionEvent.ACTION_MOVE,a.centerX()+(b.centerX()-a.centerX())*i/16f,a.centerY()+(b.centerY()-a.centerY())*i/16f);Thread.sleep(35);}motion(down,MotionEvent.ACTION_UP,b.centerX(),b.centerY());settle();
    }
    void threeColumns(String first,String second,String third){Rect a=new Rect(),b=new Rect(),c=new Rect();find(first,true).getBoundsInScreen(a);find(second,true).getBoundsInScreen(b);find(third,true).getBoundsInScreen(c);if(a.top!=b.top||b.top!=c.top||a.right>b.left||b.right>c.left||Math.abs(a.width()-c.width())>2)throw new AssertionError("Not three equal columns");report("three equal columns");}
    void top(){for(int i=0;i<8;i++){boolean moved=false;for(AccessibilityNodeInfo n:nodes())if("android.widget.ScrollView".contentEquals(n.getClassName())&&n.isScrollable()){moved=n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);break;}settle();if(!moved)break;}}
    @Override public void onStart(){
        try{
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName("ru.ladon.remote","ru.ladon.remote.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Activity app=startActivitySync(launch);settle();exists("ClickMate");
            tap("Фильм");exists("Тише −");exists("Громче +");exists("Пуск / пауза");exists("＋\n\nТачпад");threeColumns("Предыдущий","Пуск / пауза","Следующий");screenshot("film");
            tap("Медиа");exists("Громче +");exists("▶  /  Ⅱ     Пуск / пауза");screenshot("media");
            tap("Навигация");exists("Новая вкладка");
            tap("Настройки");find("Чувствительность мыши · 1.0×",false);report("default sensitivity 1x");scrollTo("Тёмная тема");tap("Тёмная тема");scrollTo("+ Создать раздел");screenshot("settings");tap("+ Создать раздел");
            field(0,"Cinema");scrollTo("+ Добавить команду");tap("+ Добавить команду");tap("Медиа");tap("Громче +");scrollTo("+ Добавить команду");tap("+ Добавить команду");tap("Медиа");tap("Пуск / пауза");
            scrollTo("+ Кнопка со своим текстом");tap("+ Кнопка со своим текстом");field(0,"Greeting");field(1,"Hello");tap("Готово");
            threeColumns("Громче +","Пуск / пауза","Greeting");
            scrollTo("+ Добавить команду");tap("+ Добавить команду");tap("Мышь");tap("Тачпад");
            screenshot("grid-editor");tap("Сохранить");exists("Cinema");exists("Greeting");threeColumns("Громче +","Пуск / пауза","Greeting");screenshot("custom");
            runOnMainSync(()->app.recreate());settle();exists("Cinema");exists("Greeting");report("custom section survives activity recreation");
            tap("Настройки");scrollTo("Cinema · изменить");tap("Cinema · изменить");
            tap("Пуск / пауза");tap("Удалить кнопку");drag("Greeting","Пустая ячейка 2");drag("Greeting","Пустая ячейка 3");tap("Сохранить");
            startActivitySync(launch);settle();
            Rect left=new Rect(),right=new Rect();find("Громче +",true).getBoundsInScreen(left);find("Greeting",true).getBoundsInScreen(right);
            if(left.top!=right.top||right.left-left.right<left.width())throw new AssertionError("Middle gap collapsed");
            String stored=getTargetContext().getSharedPreferences("MainActivity",0).getString("sections","[]");
            if(!new org.json.JSONArray(stored).getJSONObject(0).getJSONArray("buttons").getJSONObject(1).optBoolean("empty"))throw new AssertionError("Gap not saved");
            report("button / empty / button layout persisted");
            tap("Настройки");scrollTo("Cinema · изменить");tap("Cinema · изменить");
            scrollTo("+ Добавить команду");tap("+ Добавить команду");tap("Медиа");tap("Пуск / пауза");
            field(0,"Cinema2");
            drag("Пуск / пауза","Громче +");
            settle();tap("Сохранить");exists("Cinema2");
            ArrayList<String> labels=new ArrayList<>();for(AccessibilityNodeInfo n:nodes())if(n.getText()!=null)labels.add(n.getText().toString());if(labels.indexOf("Пуск / пауза")>labels.indexOf("Громче +"))throw new AssertionError("Order not saved");report("button reordering saved");
            tap("Настройки");scrollTo("Cinema2 · изменить");tap("Cinema2 · изменить");tap("Отмена");tap("Cinema2");exists("Greeting");screenshot("custom-edited");
            tap("Настройки");scrollTo("Cinema2 · изменить");tap("Cinema2 · изменить");tap("Greeting");tap("Размер, иконка и цвет");tap("Размер кнопки");tap("2 ячейки");tap("Иконка кнопки");tap("Звезда");tap("Цвет кнопки");tap("Фиолетовый");tap("Готово");tap("Сохранить");exists("★  Greeting");
            String styled=getTargetContext().getSharedPreferences("MainActivity",0).getString("sections","[]");org.json.JSONArray styledButtons=new org.json.JSONArray(styled).getJSONObject(0).getJSONArray("buttons");org.json.JSONObject styledButton=null;for(int i=0;i<styledButtons.length();i++)if("Greeting".equals(styledButtons.getJSONObject(i).optString("label")))styledButton=styledButtons.getJSONObject(i);if(styledButton==null||styledButton.optInt("span")!=2||!"★".equals(styledButton.optString("icon"))||!"#6B4EFF".equals(styledButton.optString("color")))throw new AssertionError("Button appearance not saved: "+styledButton);report("button size, icon and color saved");
            tap("Настройки");scrollTo("Порядок разделов");tap("Порядок разделов");drag("Cinema2","Мышь");tap("Сохранить");
            String savedOrder=getTargetContext().getSharedPreferences("MainActivity",0).getString("section_order","[]");
            if(!new org.json.JSONArray(savedOrder).optString(0).startsWith("custom-"))throw new AssertionError("Section order not saved: "+savedOrder);report("custom section dragged before built-in sections");
            scrollTo("Cinema2 · удалить");tap("Cinema2 · удалить");tap("Отмена");exists("Cinema2 · удалить");scrollTo("Cinema2 · удалить");tap("Cinema2 · удалить");tap("Удалить");runOnMainSync(()->app.onBackPressed());settle();exists("Левый щелчок");report("delete confirmation and cancellation");
            tap("Сканировать QR");exists("Назад");screenshot("scanner");tap("Назад");exists("Левый щелчок");report("camera scanner opens and returns to remote");
            Bundle result=new Bundle();result.putString("stream","ALL UI CHECKS PASSED\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable ex){try{screenshot("failure");}catch(Exception ignored){}Bundle result=new Bundle();result.putString("stream","FAIL: "+ex.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
