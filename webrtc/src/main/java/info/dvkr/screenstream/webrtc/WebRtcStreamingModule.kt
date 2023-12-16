package info.dvkr.screenstream.webrtc

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.elvishew.xlog.XLog
import info.dvkr.screenstream.common.StreamingModule
import info.dvkr.screenstream.common.getLog
import info.dvkr.screenstream.webrtc.internal.WebRtcEvent
import info.dvkr.screenstream.webrtc.internal.WebRtcState
import info.dvkr.screenstream.webrtc.internal.WebRtcStreamingService
import info.dvkr.screenstream.webrtc.ui.WebRtcStreamingFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope

@Single
@Named(WebRtcKoinQualifier)
public class WebRtcStreamingModule : StreamingModule {
    internal companion object {
        internal val Id: StreamingModule.Id = StreamingModule.Id("WEBRTC")
    }

    override val id: StreamingModule.Id = Id

    override val priority: Int = 10

    private val _streamingServiceIsReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val streamingServiceIsReady: StateFlow<Boolean>
        get() = _streamingServiceIsReady.asStateFlow()

    private val _webRtcStateFlow: MutableStateFlow<WebRtcState?> = MutableStateFlow(null)
    internal val webRtcStateFlow: StateFlow<WebRtcState?>
        get() = _webRtcStateFlow.asStateFlow()

    @MainThread
    override fun getName(context: Context): String {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        return context.getString(R.string.webrtc_stream_fragment_mode_global)
    }

    @MainThread
    override fun getContentDescription(context: Context): String {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        return context.getString(R.string.webrtc_stream_fragment_mode_global_description)
    }

    @MainThread
    override fun showDescriptionDialog(context: Context, lifecycleOwner: LifecycleOwner) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            icon(R.drawable.webrtc_ic_permission_dialog_24dp)
            title(R.string.webrtc_stream_fragment_mode_global)
            message(R.string.webrtc_stream_fragment_mode_global_details)
            positiveButton(android.R.string.ok)
        }
    }

    @MainThread
    override fun getFragmentClass(): Class<out Fragment> = WebRtcStreamingFragment::class.java

    internal val webRtcSettings: WebRtcSettings
        get() = requireNotNull(_scope).get()

    private var _scope: Scope? = null

    @MainThread
    @Throws(IllegalStateException::class)
    override fun createStreamingService(context: Context) {
        XLog.d(getLog("createStreamingService"))
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        if (_streamingServiceIsReady.value) {
            XLog.e(getLog("createStreamingService", "Already ready"), IllegalStateException("Already ready"))
            return
        }

        WebRtcService.startService(context, WebRtcEvent.Intentable.StartService.toIntent(context))
    }

    @MainThread
    @Throws(IllegalStateException::class)
    override fun sendEvent(event: StreamingModule.AppEvent) {
        XLog.d(getLog("sendEvent", "Event $event"))
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        when (event) {
            is StreamingModule.AppEvent.StartStream -> sendEvent(WebRtcStreamingService.InternalEvent.StartStream)
            is StreamingModule.AppEvent.StopStream -> sendEvent(WebRtcEvent.Intentable.StopStream("User action: Button"))
            else -> XLog.e(getLog("sendEvent", "Unexpected event: $event"), IllegalArgumentException("Unexpected event: $event"))
        }
    }

    @MainThread
    @Throws(IllegalStateException::class)
    internal fun sendEvent(event: WebRtcEvent) {
        XLog.d(getLog("sendEvent", "Event $event"))
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        when (event) {
            is WebRtcEvent.CreateStreamingService -> if (streamingServiceIsReady.value) {
                XLog.e(getLog("sendEvent", "Service already started. Ignoring"), IllegalStateException("Service already started. Ignoring"))
                checkNotNull(_scope)
            } else {
                check(_scope == null)

                val scope = WebRtcKoinScope().scope
                _scope = scope
                _webRtcStateFlow.value = WebRtcState()
                scope.get<WebRtcStreamingService> { parametersOf(event.service, _webRtcStateFlow) }.start()
                _streamingServiceIsReady.value = true
            }

            else -> if (_streamingServiceIsReady.value)
                requireNotNull(_scope).get<WebRtcStreamingService>().sendEvent(event)
            else
                XLog.e(getLog("sendEvent", "Module not active. Ignoring"), IllegalStateException("$event: Module not active. Ignoring"))
        }
    }

    @MainThread
    @Throws(IllegalStateException::class)
    override suspend fun destroyStreamingService() {
        XLog.d(getLog("destroyStreamingService"))
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        _scope?.let { scope ->
            scope.get<WebRtcStreamingService>().destroyService()
            _webRtcStateFlow.value = null
            scope.close()
            _scope = null
            _streamingServiceIsReady.value = false
        } ?: XLog.i(getLog("destroyStreamingService", "Scope is null"))

        XLog.d(getLog("destroyStreamingService", "Done"))
    }
}