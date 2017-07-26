import com.emotiv.Iedk.*;
import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.*;


void setup(){
    Pointer eEvent = Edk.INSTANCE.IEE_EmoEngineEventCreate();
}