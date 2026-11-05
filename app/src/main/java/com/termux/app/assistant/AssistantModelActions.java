package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiRuntimePresence;
import com.termux.ai.TaiSettings;

import org.json.JSONObject;

/**
 * The real {@link AssistantModelStatus.Actions}: each one is a call the Model centre and the local
 * API already make. Blocking; run off the main thread.
 */
final class AssistantModelActions implements AssistantModelStatus.Actions {
    private final Context appContext;

    AssistantModelActions(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public AssistantModelStatus.ActionResult setDefaultAssistantModel(@NonNull String modelId) {
        // The pick behind tai_role_default_assistant, the same one the Model centre writes.
        TaiFunctionModels.forContext(appContext).set(TaiFunction.ASSISTANT, modelId);
        return new AssistantModelStatus.ActionResult(true, "", "");
    }

    @NonNull
    @Override
    public AssistantModelStatus.ActionResult download(@NonNull String modelId) {
        try {
            return result(TaiManager.getInstance(appContext).downloadCatalogModel(modelId));
        } catch (Exception e) {
            return failure(e);
        }
    }

    @NonNull
    @Override
    public AssistantModelStatus.ActionResult cancelDownload(@NonNull String modelId) {
        try {
            return result(TaiManager.getInstance(appContext)
                .cancelDownload(new JSONObject().put("modelId", modelId).toString()));
        } catch (Exception e) {
            return failure(e);
        }
    }

    @NonNull
    @Override
    public AssistantModelStatus.ActionResult load() {
        try {
            // No model in the body: the default assistant model.
            return result(TaiManager.getInstance(appContext).loadModel("{}"));
        } catch (Exception e) {
            return failure(e);
        }
    }

    @NonNull
    @Override
    public AssistantModelStatus.ActionResult unload() {
        try {
            TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
            String id = presence.modelId != null ? presence.modelId
                : new TaiSettings(appContext).getDefaultAssistantModel();
            return result(TaiManager.getInstance(appContext).unloadChatModel(id));
        } catch (Exception e) {
            return failure(e);
        }
    }

    @NonNull
    private static AssistantModelStatus.ActionResult result(@NonNull JSONObject json) {
        boolean ok = json.optBoolean("ok", json.optInt("_statusCode", 200) < 400);
        return new AssistantModelStatus.ActionResult(ok, json.optString("error", ""),
            json.optString("message", ""));
    }

    @NonNull
    private static AssistantModelStatus.ActionResult failure(@NonNull Exception e) {
        return new AssistantModelStatus.ActionResult(false, "error", String.valueOf(e.getMessage()));
    }
}
