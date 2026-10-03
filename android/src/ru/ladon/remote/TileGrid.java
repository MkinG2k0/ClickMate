package ru.ladon.remote;

import android.content.Context;
import android.content.ClipData;
import android.graphics.Rect;
import android.view.*;
import java.util.*;

/** Three equal columns. Full-width widgets start on a new row. */
public final class TileGrid extends ViewGroup {
    public interface Drop { void move(int from,int to); }
    final ArrayList<Integer> spans=new ArrayList<>(),heights=new ArrayList<>();
    final ArrayList<Rect> cells=new ArrayList<>();
    final int gap;
    int dragged=-1;
    final Drop drop;
    android.widget.ScrollView scrollParent;
    int scrollStep;
    final Runnable autoScroll=new Runnable(){public void run(){if(dragged<0||scrollParent==null||scrollStep==0)return;scrollParent.scrollBy(0,scrollStep);postDelayed(this,40);}};
    public TileGrid(Context context,Drop drop){super(context);this.drop=drop;gap=dp(4);setClipToPadding(false);}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void tile(View view,int span,int height){
        final int index=getChildCount();spans.add(span);heights.add(dp(height));cells.add(new Rect());addView(view);
        if(drop==null)return;
        view.setOnLongClickListener(v->{
            if(getContext() instanceof android.app.Activity){View focus=((android.app.Activity)getContext()).getCurrentFocus();if(focus!=null){((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
            dragged=index;v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);boolean started=v.startDragAndDrop(ClipData.newPlainText("tile",""),new View.DragShadowBuilder(v),this,0);if(!started)dragged=-1;return started;
        });
        view.setOnDragListener((v,e)->{
            if(e.getLocalState()!=this)return false;
            switch(e.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:return true;
                case DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(.45f);return true;
                case DragEvent.ACTION_DRAG_LOCATION:edgeScroll(v,e.getY());return true;
                case DragEvent.ACTION_DRAG_EXITED:v.setAlpha(1f);return true;
                case DragEvent.ACTION_DROP:
                    v.setAlpha(1f);int from=dragged;dragged=-1;removeCallbacks(autoScroll);scrollStep=0;
                    if(from>=0&&from!=index)post(()->drop.move(from,index));return true;
                case DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1f);dragged=-1;removeCallbacks(autoScroll);scrollStep=0;return true;
                default:return true;
            }
        });
    }
    void edgeScroll(View tile,float localY){
        if(scrollParent==null){ViewParent parent=getParent();while(parent!=null){if(parent instanceof android.widget.ScrollView){scrollParent=(android.widget.ScrollView)parent;break;}parent=parent.getParent();}}
        if(scrollParent==null)return;Rect visible=new Rect();scrollParent.getGlobalVisibleRect(visible);int[] origin=new int[2];tile.getLocationOnScreen(origin);int y=origin[1]+(int)localY;
        int step=y<visible.top+dp(44)?-dp(10):y>visible.bottom-dp(44)?dp(10):0;
        if(step==scrollStep)return;scrollStep=step;removeCallbacks(autoScroll);if(step!=0)post(autoScroll);
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(autoScroll);super.onDetachedFromWindow();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),column=0,y=0,rowHeight=0;
        for(int i=0;i<getChildCount();i++){
            int span=spans.get(i),height=heights.get(i);
            if(column+span>3){y+=rowHeight;column=0;rowHeight=0;}
            int left=width*column/3+gap,right=width*(column+span)/3-gap;
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(0,right-left),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
            cells.get(i).set(left,y+gap,right,y+gap+height);rowHeight=Math.max(rowHeight,height+gap*2);column+=span;
            if(column==3){y+=rowHeight;column=0;rowHeight=0;}
        }
        setMeasuredDimension(width,resolveSize(y+rowHeight,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){Rect cell=cells.get(i);getChildAt(i).layout(cell.left,cell.top,cell.right,cell.bottom);}}
}
