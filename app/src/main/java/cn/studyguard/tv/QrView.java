package cn.studyguard.tv;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/** Local vector QR, integer-sized modules and a four-module quiet border. No network dependency. */
final class QrView extends View {
    private final QrCode qr;
    private final Paint paint=new Paint();
    QrView(Context c,String link){super(c);qr=QrCode.encodeText(link,QrCode.Ecc.MEDIUM);setContentDescription("手机扫码配对二维码");}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(Color.WHITE);int unit=Math.min(getWidth(),getHeight())/(qr.size+8);if(unit<1)return;int left=(getWidth()-qr.size*unit)/2,top=(getHeight()-qr.size*unit)/2;paint.setColor(Color.BLACK);paint.setAntiAlias(false);for(int y=0;y<qr.size;y++)for(int x=0;x<qr.size;x++)if(qr.getModule(x,y))canvas.drawRect(left+x*unit,top+y*unit,left+(x+1)*unit,top+(y+1)*unit,paint);}
}
