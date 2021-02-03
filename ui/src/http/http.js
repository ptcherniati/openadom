import config from "@/config";

class HttpClient {
    login(user) {
        let formData = new FormData();
        formData.append("login", user.login);
        formData.append("password", user.password);
        return this.post('login', formData);
    }

    logOut() {
        return this.delete('logout');
    }

    loadDataset(dataset, applicationName) {
        return this.get(`applications/${applicationName}/data/${dataset}`);
    }

    loadReference(reference, applicationName) {
        return this.get(`applications/${applicationName}/references/${reference}`);
    }

    uploadReference(reference, applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return this.post(`applications/${applicationName}/references/${reference}`, formData);
    }

    uploadDataset(dataset, applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return this.post(`applications/${applicationName}/data/${dataset}`, formData);
    }

    loadApplications() {
        return this.get(`applications/`);
    }

    loadApplicationConfiguration(applicationName) {
        return this.get(`applications/${applicationName}`);
    }

    uploadFile(id) {
        return this.get(`files/${id}`);
    }

    loadApplication(applicationName, file) {
        let formData = new FormData();
        formData.append("file", file);
        return this.post(`applications/${applicationName}`, formData);
    }

    post(endpoint, body) {
        const url = this.getUrl(endpoint)
        return fetch(url,
            {
                credentials: "include",
                method: "POST",
                body: body
            }
        )
    }

    delete(endpoint) {
        const url = this.getUrl(endpoint)
        return fetch(url,
            {
                credentials: "include",
                method: "DELETE"
            }
        )
    }

    get(endpoint) {
        const url = this.getUrl(endpoint)
        return fetch(url,
            {
                credentials: "include"
            }
        )
    }

    getUrl(endpoint) {
        return `${config.API_URL}${endpoint}`
    }
}
const http = new HttpClient()
export default http