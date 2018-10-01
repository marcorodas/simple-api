package pe.mrodas.rest;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;

public class ApiDA {

    public interface ResponseBodyParser {
        ApiError parse(ResponseBody body, ApiError apiError);
    }

    public interface OnSuccess<T> {
        void onSuccess(Response<T> response);
    }

    public interface OnErrorEnqueue {
        void onError(ApiError apiError);
    }

    public interface OnErrorExecute {
        void onError(ApiError apiError) throws ApiException;
    }

    public interface ProgressMethod {
        void showProgress(boolean show);
    }

    private Retrofit retrofit;
    private boolean debugMode;
    private ResponseBodyParser errorBodyParser;
    private OnErrorExecute defaultErrorExecute;
    private OnErrorEnqueue defaultErrorEnqueue;
    private ProgressMethod progress;

    public void setBaseUrl(String baseUrl, OkHttpClient client, Converter.Factory... factories) {
        if (baseUrl == null) {
            retrofit = null;
            return;
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl.concat("/");
        Retrofit.Builder builder = new Retrofit.Builder().baseUrl(baseUrl);
        if (client != null) {
            builder.client(client);
        }
        if (factories != null) {
            for (Converter.Factory factory : factories) {
                builder.addConverterFactory(factory);
            }
        }
        retrofit = builder.build();
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isDebugMode() {
        return this.debugMode;
    }

    public void setErrorBodyParser(ResponseBodyParser errorBodyParser) {
        this.errorBodyParser = errorBodyParser;
    }

    public void setDefaultErrorExecute(OnErrorExecute defaultErrorExecute) {
        this.defaultErrorExecute = defaultErrorExecute;
    }

    public void setDefaultErrorEnqueue(OnErrorEnqueue defaultErrorEnqueue) {
        this.defaultErrorEnqueue = defaultErrorEnqueue;
    }

    public void setProgress(ProgressMethod progress) {
        this.progress = progress;
    }

    /***
     * Throws a IllegalStateException(Base URL required)
     * if URL is not set (retrofit == null)
     */
    private void checkRetrofit() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder().build();
        }
    }

    public void setClient(OkHttpClient client) {
        this.checkRetrofit();
        retrofit = retrofit.newBuilder().client(client).build();
    }

    public HttpUrl getBaseUrl() {
        this.checkRetrofit();
        return retrofit.baseUrl();
    }

    public <S> S create(Class<S> aClass) {
        this.checkRetrofit();
        return retrofit.create(aClass);
    }

    public <T> T execute(Call<T> call) throws IOException, ApiException {
        return this.execute(call, null);
    }

    public <T> T execute(Call<T> call, OnErrorExecute onErrorExecute) throws IOException, ApiException {
        Response<T> response = call.execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        ApiError apiError = this.buildError(call, response);
        if (apiError != null) {
            if (onErrorExecute != null) {
                onErrorExecute.onError(apiError);
            } else if (defaultErrorExecute != null) {
                defaultErrorExecute.onError(apiError);
            } else {
                throw new ApiException(apiError);
            }
        }
        return null;
    }

    public <T> void enqueue(Call<T> call, OnSuccess<T> success) {
        this.enqueue(call, success, null);
    }

    public <T> void enqueue(Call<T> call, OnSuccess<T> success, OnErrorEnqueue error) {
        call.enqueue(new ApiCallback<T>(this, success, error));
    }

    void showProgress(boolean show) {
        if (progress != null) {
            progress.showProgress(show);
        }
    }

    void handleErrorEnqueue(ApiError apiError, OnErrorEnqueue error) {
        if (apiError != null) {
            if (error != null) {
                error.onError(apiError);
            } else if (defaultErrorEnqueue != null) {
                defaultErrorEnqueue.onError(apiError);
            }
        }
    }

    public <T> boolean isResponseBodyNullError(Response<T> response, int... httpStatusCodesException) {
        if (httpStatusCodesException == null) {
            return response.body() == null;
        }
        if (response.body() == null) {
            for (int statusCode : httpStatusCodesException) {
                if (response.raw().code() == statusCode) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    <T> ApiError buildError(Call<T> call, Response<T> response) {
        ApiError error = new ApiError(response.code())
                .setUserMessage(response.message());
        ResponseBody errorBody = response.errorBody();
        if (debugMode) {
            String headers = response.headers().toString().replace("\n", ", ");
            String logMessage = String.format("\n%s\nHeaders{%s}\n%s",
                    call.request(), headers.substring(0, headers.length() - 2), response
            );
            if (errorBody != null) {
                if (errorBodyParser == null) {
                    try {
                        logMessage += "\nBody:" + errorBody.string();
                        return error.setLogMessage(logMessage);
                    } catch (IOException ignored) {
                    }
                }
                return errorBodyParser.parse(errorBody, error)
                        .prependLogMessage(logMessage);
            }
            return error.setLogMessage(logMessage);
        } else if (errorBodyParser != null && errorBody != null) {
            return errorBodyParser.parse(errorBody, error)
                    .prependLogMessage(response.toString());
        }
        return error;
    }
}