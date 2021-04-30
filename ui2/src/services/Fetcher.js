import config from "@/config";
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { EventEmitter } from "events";

export const CREDENTIALS_LOST_EVENT = "credentialsLost";

export class Fetcher extends EventEmitter {
  notifyCrendentialsLost() {
    this.emit(CREDENTIALS_LOST_EVENT);
  }

  async post(url, data) {
    const formData = this.convertToFormData(data);

    const response = await fetch(`${config.API_URL}${url}`, {
      method: "POST",
      mode: "cors",
      credentials: "include",
      body: formData,
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
      if (response.ok && response.status !== HttpStatusCodes.NO_CONTENT) {
        const text = await response.text();
        return Promise.resolve(JSON.parse(text));
      } else if (response.status === HttpStatusCodes.UNAUTHORIZED) {
        this.notifyCrendentialsLost();
      }
    } catch (error) {
      console.error(error);
    }

    return Promise.reject({ status: response.status });
  }

  convertToFormData(body) {
    let formData = new FormData();
    if (body) {
      for (const [key, value] of Object.entries(body)) {
        formData.append(key.toString(), value.toString());
      }
    }
    return formData;
  }
}
