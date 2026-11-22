package cn.studyguard.tv;
import android.content.Context;
import android.view.*;
import android.widget.*;
import cn.studyguard.core.Question;
import cn.studyguard.core.Rules;

/** Shared uncluttered TV question surface, including explicit remote focus paths. */
final class QuizView extends FrameLayout {
    interface Answer {void choose(int index);}
    final Button[] options=new Button[4];
    private final RegretFace regret;
    private final Runnable parent;
    QuizView(Context context,String hint,Question q,Answer answer,Runnable parent) {
        super(context);this.parent=parent;Ui.bg(this,Ui.root(context));
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setPadding(Ui.dp(context,48),Ui.dp(context,60),Ui.dp(context,48),Ui.dp(context,24));
        addView(scroll,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        LinearLayout content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(content,new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));

        // Center the native TV icon and enlarged hint together; no bubble or border.
        LinearLayout hintRow=new LinearLayout(context);hintRow.setGravity(Gravity.CENTER);
        hintRow.addView(new Mascot(context),new LinearLayout.LayoutParams(Ui.dp(context,48),Ui.dp(context,48)));
        TextView reminder=Ui.text(context,hint,32,Ui.MUTED);reminder.setGravity(Gravity.CENTER);
        int width=context.getResources().getDisplayMetrics().widthPixels-Ui.dp(context,154);
        reminder.setMaxWidth(Math.max(Ui.dp(context,160),width));
        LinearLayout.LayoutParams hintParams=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(Ui.dp(context,10),0,0,0);hintRow.addView(reminder,hintParams);
        content.addView(hintRow,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));

        String prompt=displayPrompt(q);
        TextView title=Ui.title(context,prompt,prompt.length()>70?39:prompt.length()>45?44:48);
        title.setTextColor(Ui.GOLD);title.setGravity(Gravity.CENTER);title.setPadding(0,Ui.dp(context,20),0,Ui.dp(context,24));content.addView(title);
        for(int row=0;row<2;row++) {
            LinearLayout line=new LinearLayout(context);line.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<2;col++) {
                int index=row*2+col;
                // One flat style for every option; focus uses a green fill and gold type.
                Button button=new Button(context);
                button.setText(q.choices.get(index));
                button.setTextSize(q.choices.get(index).length()>24?26:30);
                button.setTextColor(Ui.BG);button.setAllCaps(false);
                button.setCompoundDrawablePadding(Ui.dp(context,12));
                button.setCompoundDrawablesWithIntrinsicBounds(new Ui.Badge(context,String.valueOf((char)('A'+index))),null,null,null);
                button.setPadding(Ui.dp(context,10),Ui.dp(context,8),Ui.dp(context,10),Ui.dp(context,8));
                button.setMinHeight(Ui.dp(context,104));
                Ui.flatButton(button);
                button.setOnClickListener(v->answer.choose(index));
                button.setId(Ui.id());
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1);params.setMargins(Ui.dp(context,7),Ui.dp(context,7),Ui.dp(context,7),Ui.dp(context,7));
                line.addView(button,params);options[index]=button;
            }
            content.addView(line,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        for(int i=0;i<4;i++) {
            Button b=options[i];b.setNextFocusLeftId(options[i%2==0?i:i-1].getId());b.setNextFocusRightId(options[i%2==0?i+1:i].getId());
            b.setNextFocusUpId(options[i<2?i:i-2].getId());b.setNextFocusDownId(options[i<2?i+2:i].getId());
        }
        Button manage=Ui.button(context,"家长扫码",parent);
        Ui.flatButton(manage);manage.setOnFocusChangeListener(null);manage.setTextSize(14);manage.setMinHeight(Ui.dp(context,44));
        manage.setContentDescription("家长扫码，手机或电脑管理；也可按菜单键");manage.setId(Ui.id());
        LayoutParams manageParams=new LayoutParams(Ui.dp(context,104),Ui.dp(context,48),Gravity.TOP|Gravity.RIGHT);
        manageParams.setMargins(0,Ui.dp(context,8),Ui.dp(context,16),0);addView(manage,manageParams);
        options[0].setNextFocusUpId(manage.getId());options[1].setNextFocusUpId(manage.getId());
        manage.setNextFocusDownId(options[1].getId());
        regret=new RegretFace(context);
        LayoutParams face=new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.CENTER);
        addView(regret,face);
    }
    /** v0.7.1: quiz surface shows the pure question — drop a leading subject label such as "唐诗接句：".
     *  Data and records keep the original prompt; only this display strips it. */
    static String displayPrompt(Question q) {
        String prompt=q.prompt,name=Rules.subjectName(q.subject),full=name+"：",half=name+":";
        if(prompt.startsWith(full))return prompt.substring(full.length());
        if(prompt.startsWith(half))return prompt.substring(half.length());
        return prompt;
    }
    /** First wrong answer feedback: transient drawn regret face plus one "哦豁" sound. */
    void showRegret() {
        regret.show(1600);
        OopsSound.play(getContext());
    }
    @Override protected void onAttachedToWindow() {super.onAttachedToWindow();options[0].requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU) {if(event.getAction()==KeyEvent.ACTION_UP)parent.run();return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK)return true;
        return super.dispatchKeyEvent(event);
    }
}
