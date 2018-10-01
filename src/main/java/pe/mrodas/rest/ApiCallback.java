package pe.mrodas.rest;

import java.net.HttpURLConnection;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiCallback<T> implements Callback<T> {

    public static final String NETWORK_FAIL = "Network Fail!";
    private final ApiDA apiDA;
    private final ApiDA.OnSuccess<T> success;
    private final ApiDA.OnErrorEnqueue error;

    ApiCallback(ApiDA apiDA, ApiDA.OnSuccess<T> success, ApiDA.OnErrorEnqueue error) {
        apiDA.showProgress(true);
        this.apiDA = apiDA;
        this.success = success;
        this.error = error;
    }

    public void onResponse(Call<T> call, Response<T> response) {
        apiDA.showProgress(false);
        if (response.isSuccessful()) {
            if (success != null) {
                success.onSuccess(response);
            }
        } else {
            ApiError apiError = apiDA.buildError(call, response);
            apiDA.handleErrorEnqueue(apiError, error);
        }
    }

    public void onFailure(Call<T> call, Throwable t) {
        apiDA.showProgress(false);
        ApiError apiError = new ApiError(HttpURLConnection.HTTP_CLIENT_TIMEOUT)
                .setUserMessage(NETWORK_FAIL)
                .setStacktrace((Exception) t);
        apiDA.handleErrorEnqueue(apiError, error);
    }
}
