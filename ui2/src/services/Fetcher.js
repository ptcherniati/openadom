import config from "@/config";
import app from "@/main";
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { Locales } from "@/utils/LocaleUtils";

export const LOCAL_STORAGE_LANG = "lang";
export const LOCAL_STORAGE_AUTHENTICATED_USER = "authenticatedUser";

export class Fetcher {
  async post(url, data) {
    const formData = this.convertToFormData(data);

    const response = await fetch(`${config.API_URL}${url}`, {
      method: "POST",
      mode: "cors",
      credentials: "include",
      body: formData,
      headers: {
        "Accept-Language": this.getUserPrefLocale(),
      },
    });

    return this._handleResponse(response);
  }

  async put(url, data) {
    const formData = this.convertToFormData(data);
    const response = await fetch(`${config.API_URL}${url}`, {
      method: "PUT",
      mode: "cors",
      credentials: "include",
      body: formData,
      headers: {
        "Accept-Language": this.getUserPrefLocale(),
      },
    });

    return this._handleResponse(response);
  }

  async get(url, params = {}) {
    const path = new URL(url, config.API_URL);

    Object.entries(params).forEach(([name, value]) => {
      if (Array.isArray(value)) {
        value.forEach((v) => {
          path.searchParams.append(name, v);
        });
      } else {
        path.searchParams.append(name, value);
      }
    });

    const response = await fetch(path, {
      method: "GET",
      mode: "cors",
      credentials: "include",
      headers: {
        "Accept-Language": this.getUserPrefLocale(),
      },
    });

    return this._handleResponse(response);
  }

  async delete(url, data) {
    const formData = this.convertToFormData(data);
    const response = await fetch(`${config.API_URL}${url}`, {
      method: "DELETE",
      mode: "cors",
      credentials: "include",
      body: formData,
      headers: {
        "Accept-Language": this.getUserPrefLocale(),
      },
    });

    if (response.ok) {
      return Promise.resolve(response);
    } else if (response.status === HttpStatusCodes.UNAUTHORIZED) {
      this.notifyCrendentialsLost();
    }

    return Promise.reject({ status: response.status });
  }

  async _handleResponse(response) {
    try {
      const text = await response.json();
      if (response.ok && response.status !== HttpStatusCodes.NO_CONTENT) {
        return Promise.resolve(text);
      }
      return Promise.reject({ httpResponseCode: response.status, content: text });
    } catch (error) {
      console.error(error);
    }
    if (response.ok) {
      return Promise.resolve();
    }
    return Promise.reject({ httpResponseCode: response.status });
  }

  async downloadFile(urlPath) {
    const url = new URL(`${config.API_URL}${urlPath}`);
    window.open(url, "_blank");
  }

  notifyCrendentialsLost() {
    localStorage.removeItem(LOCAL_STORAGE_AUTHENTICATED_USER);
    app.$router.push("/login").catch(() => {});
  }

  convertToFormData(body) {
    let formData = new FormData();
    if (body) {
      for (const [key, value] of Object.entries(body)) {
        formData.append(key.toString(), value);
      }
    }
    return formData;
  }

  getUserPrefLocale() {
    const browserLocale = window.navigator.language.substring(0, 2);

    return (
      localStorage.getItem(LOCAL_STORAGE_LANG) ||
      (Object.values(Locales).includes(browserLocale) && browserLocale) ||
      Locales.FRENCH
    );
  }
}
