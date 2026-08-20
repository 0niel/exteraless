package app.exteraless.nowplaying;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public final class ProfileMusicStamp implements NotificationCenter.NotificationCenterDelegate {

    public interface Callback {
        void onFinished(boolean ok, int reason);
    }

    public static final int REASON_OK = 0;
    public static final int REASON_NO_MUSIC = 1;
    public static final int REASON_DOWNLOAD = 2;
    public static final int REASON_UPLOAD = 3;
    public static final int REASON_SAVE = 4;

    private static final long TIMEOUT = 120_000L;

    private final int account;
    private final String nick;
    private final Callback callback;
    private final long selfId;

    private TLRPC.Document source;
    private String stampedName;
    private String awaitedFileName;
    private boolean finished;

    private final Runnable timeout = () -> finish(false, REASON_UPLOAD);

    public static void apply(int account, String nick, Callback callback) {
        new ProfileMusicStamp(account, nick, callback).start();
    }


    private ProfileMusicStamp(int account, String nick, Callback callback) {
        this.account = account;
        this.nick = nick;
        this.callback = callback;
        this.selfId = UserConfig.getInstance(account).getClientUserId();
    }

    private void start() {
        TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(selfId);
        if (full == null || full.saved_music == null) {
            finish(false, REASON_NO_MUSIC);
            return;
        }
        source = full.saved_music;
        String fileName = FileLoader.getDocumentFileName(source);
        String current = ProfileMusicMark.nickFrom(fileName, selfId);
        boolean clean = TextUtils.equals(ZeroWidthCodec.stripToString(performerOf(source)), performerOf(source));
        if (clean && (TextUtils.equals(current, nick)
                || (TextUtils.isEmpty(current) && TextUtils.isEmpty(nick)))) {
            finish(true, REASON_OK);
            return;
        }
        if (TextUtils.isEmpty(nick)) {
            stampedName = ProfileMusicMark.strip(fileName);
        } else {
            stampedName = ProfileMusicMark.stamp(fileName, nick, selfId);
            if (ProfileMusicMark.nickFrom(stampedName, selfId) == null) {
                finish(false, REASON_SAVE);
                return;
            }
        }
        if (TextUtils.isEmpty(stampedName)) {
            stampedName = "audio.mp3";
        }

        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.fileLoaded);
        center.addObserver(this, NotificationCenter.fileLoadFailed);
        center.addObserver(this, NotificationCenter.messageReceivedByServer);
        AndroidUtilities.runOnUIThread(timeout, TIMEOUT);

        File file = localFile();
        if (file != null) {
            upload(file);
        } else {
            awaitedFileName = FileLoader.getAttachFileName(source);
            FileLoader.getInstance(account).loadFile(source,
                    MessagesController.getInstance(account).getUser(selfId),
                    FileLoader.PRIORITY_HIGH, 0);
        }
    }

    private File localFile() {
        FileLoader loader = FileLoader.getInstance(account);
        File file = loader.getPathToAttach(source, null, false);
        if (file != null && file.exists() && file.length() > 0) {
            return file;
        }
        file = loader.getPathToAttach(source, null, true);
        if (file != null && file.exists() && file.length() > 0) {
            return file;
        }
        return null;
    }

    private void upload(File file) {
        TLRPC.TL_document out = new TLRPC.TL_document();
        out.id = 0;
        out.access_hash = 0;
        out.dc_id = 0;
        out.file_reference = new byte[0];
        out.date = ConnectionsManager.getInstance(account).getCurrentTime();
        out.mime_type = source.mime_type != null ? source.mime_type : "audio/mpeg";
        out.size = file.length();

        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
        audio.duration = durationOf(source);
        audio.title = ZeroWidthCodec.stripToString(titleOf(source));
        if (audio.title == null) {
            audio.title = "";
        }
        audio.performer = ZeroWidthCodec.stripToString(performerOf(source));
        if (audio.performer == null) {
            audio.performer = "";
        }
        audio.flags |= 1;
        audio.flags |= 2;
        out.attributes.add(audio);

        TLRPC.TL_documentAttributeFilename name = new TLRPC.TL_documentAttributeFilename();
        name.file_name = stampedName;
        out.attributes.add(name);

        attachCover(out, file);

        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                out, null, file.getAbsolutePath(), selfId, null, null, null, null, null, null,
                false, 0, 0, 0, null, null, false));
    }

    private static void attachCover(TLRPC.TL_document out, File file) {
        Bitmap cover = null;
        try {
            AudioInfo info = AudioInfo.getAudioInfo(file);
            if (info != null) {
                cover = info.getCover();
            }
            if (cover == null) {
                return;
            }
            TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(cover, 132, 132, 55, false);
            if (thumb != null) {
                out.thumbs.add(thumb);
                out.flags |= 1;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cover != null) {
                cover.recycle();
            }
        }
    }

    private void saveMusic(TLRPC.Document document) {
        TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
        req.unsave = false;
        req.id = inputDocument(document);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                finish(false, REASON_SAVE);
                return;
            }
            unsaveSource();
            TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(selfId);
            if (full != null) {
                full.saved_music = document;
                MessagesStorage.getInstance(account).updateUserInfo(full, false);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.profileMusicUpdated, selfId);
            }
            finish(true, REASON_OK);
        }));
    }

    private void unsaveSource() {
        TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
        req.unsave = true;
        req.id = inputDocument(source);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            MessagesController.SavedMusicList list = MediaController.getInstance().currentSavedMusicList;
            if (list != null && list.dialogId == selfId) {
                MediaController.getInstance().currentSavedMusicList = null;
            }
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.profileMusicUpdated, selfId);
        }));
    }

    private static TLRPC.TL_inputDocument inputDocument(TLRPC.Document document) {
        TLRPC.TL_inputDocument input = new TLRPC.TL_inputDocument();
        input.id = document.id;
        input.access_hash = document.access_hash;
        input.file_reference = document.file_reference != null ? document.file_reference : new byte[0];
        return input;
    }

    @Override
    public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (finished) {
            return;
        }
        if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
            if (awaitedFileName == null || !awaitedFileName.equals(args[0])) {
                return;
            }
            if (id == NotificationCenter.fileLoadFailed) {
                finish(false, REASON_DOWNLOAD);
                return;
            }
            File file = localFile();
            if (file == null) {
                finish(false, REASON_DOWNLOAD);
                return;
            }
            awaitedFileName = null;
            upload(file);
        } else if (id == NotificationCenter.messageReceivedByServer) {
            if (!(args[2] instanceof TLRPC.Message)) {
                return;
            }
            TLRPC.Message message = (TLRPC.Message) args[2];
            if (message.media == null || message.media.document == null) {
                return;
            }
            if (!TextUtils.equals(FileLoader.getDocumentFileName(message.media.document), stampedName)) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(timeout);
            saveMusic(message.media.document);
        }
    }

    private void finish(boolean ok, int reason) {
        if (finished) {
            return;
        }
        finished = true;
        AndroidUtilities.cancelRunOnUIThread(timeout);
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.removeObserver(this, NotificationCenter.fileLoaded);
        center.removeObserver(this, NotificationCenter.fileLoadFailed);
        center.removeObserver(this, NotificationCenter.messageReceivedByServer);
        if (callback != null) {
            callback.onFinished(ok, reason);
        }
    }

    private static String performerOf(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.performer;
            }
        }
        return null;
    }

    private static String titleOf(TLRPC.Document document) {
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.title;
            }
        }
        return null;
    }

    private static int durationOf(TLRPC.Document document) {
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return (int) attribute.duration;
            }
        }
        return 0;
    }
}
