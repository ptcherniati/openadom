import axios from "axios";
import config from "@/config";
import {
    storage,
    Storage
} from "@/storage";

class HttpClient {
    helloWorld() {
        return axios.get(`${config.API_URL}`);
    }

    login(user) {
        let formData = new FormData();
        formData.append("login", user.login);
        formData.append("password", user.password);
        return fetch(
            `${config.API_URL}login`, {
                method: "POST",
                body: formData,
                credentials: "include"
            }
        )
    }

    logOut() {
        return fetch(
            `${config.API_URL}logout`, {
                method: "DELETE",
            }
        )
    }

    loadDataset(dataset, applicationName) {
        return fetch(`${config.API_URL}applications/${applicationName}/data/${dataset}`, {
            credentials: "include"
        });
    }

    loadReference(reference, applicationName) {
        return fetch(`${config.API_URL}applications/${applicationName}/references/${reference}`, {
            credentials: "include"
        });
    }

    uploadReference(reference, applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return fetch(`${config.API_URL}applications/${applicationName}/references/${reference}`, {
            method: "POST",
            body: formData,
            credentials: "include"
        });
    }

    uploadDataset(dataset, applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return fetch(`${config.API_URL}applications/${applicationName}/data/${dataset}`, {
            method: "POST",
            body: formData,
            credentials: "include"
        });
    }

    loadApplications() {
        return fetch(`${config.API_URL}applications/`, {
            credentials: "include"
        });
    }

    loadApplicationConfiguration(applicationName) {
        return fetch(`${config.API_URL}applications/${applicationName}`, {
            credentials: "include"
        });
    }

    uploadFile(id) {
        return fetch(`${config.API_URL}files/${id}`, {
            credentials: "include"
        });
    }

    loadApplication(applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return fetch(`${config.API_URL}applications/${applicationName}`, {
            method: "POST",
            body: formData,
            credentials: "include"
        });
    }

    buildConfig(config) {
        const headers = config == null ? {} : config;
        if (storage.get(Storage.TOKEN_KEY)) {
            headers["si-ore-jwt"] = storage.get(Storage.TOKEN_KEY);
        }
        return {
            headers
        };
    }
}
const http = new HttpClient()
export default http