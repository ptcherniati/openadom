import Vue from 'vue'
import Vuex from 'vuex'
import defaultData from '@/data'
import http from "@/http/http.js";
import EventBus from '@/eventBus';

Vue.use(Vuex)

export default new Vuex.Store({
    state: {
        data: defaultData,
        user: {
            init: true,
            login: "poussin",
            password: "xxxxxxxx"
        },
        applications: [],
        application: null,
        applicationName: null,
        configuration: null,
        referenceType: null,
        dataType: null,
        referenceName: null,
        referenceValue: null,
        referenceDescription: null,
        datasetName: null,
        datasetValue: null,
        datasetDescription: null,
    },
    mutations: {
        removeUser(state) {
            this.state.user = null;
        },
        setUser(state, payload) {
            if (payload.ok) {
                this.state.user = payload;
                EventBus.$emit('user:connected', payload)
                this.dispatch('loadApplications')
            } else {
                EventBus.$emit('user:disconnected', payload)
            }
        },
        logOut(state, payload) {
            if (payload.ok) {
                EventBus.$emit('user:disconnected', payload)
            } else {
                EventBus.$emit('user:disconnected:error', payload)
            }
            this.state.user = {
                init: true,
            };
            this.state.data = defaultData;
            this.state.applications = [];
            this.state.application = null;
            this.state.applicationName = null;
            this.state.configuration = null;
            this.state.referenceType = null;
            this.state.dataType = null;
            this.state.referenceName = null;
            this.state.referenceValue = null;
            this.state.referenceDescription = null;
            this.state.datasetName = null;
            this.state.datasetValue = null;
            this.state.datasetDescription = null;
        },
        setApplications(state, payload) {
            this.state.applications = payload;
        },
        applicationLoaded(state, payload) {
            EventBus.$emit('application:loaded', payload)
        },
        setApplication(state, payload) {
            this.state.application = payload;
            this.state.applicationName = payload == null ? null : payload.name;
            this.state.configuration = payload.configuration;
            this.state.dataType = payload.dataType;
            this.state.referenceType = payload.referenceType;
        },
        setReference(state, payload) {
            if (payload.status) {
                EventBus.$emit('reference:loaded:error', payload);
            } else {
                EventBus.$emit('reference:loaded', payload);
                this.state.referenceName = payload.referenceName;
                this.state.referenceDescription = payload.referenceDescription;
                this.state.referenceValue = payload.referenceValue;
            }
        },
        referenceUploaded(state, payload) {
            EventBus.$emit('reference:uploaded', payload);
        },
        datasetUploaded(state, payload) {
            EventBus.$emit('dataset:uploaded', payload);
        },
        setReferenceDescription(state, payload) {
            this.state.referenceDescription = payload;
        },
        setDataset(state, payload) {
            if (payload.status) {
                EventBus.$emit('dataset:loaded:error', payload);
            } else {
                EventBus.$emit('dataset:loaded', payload);
                this.state.datasetName = payload.datasetName;
                this.state.datasetDescription = payload.datasetDescription;
                this.state.datasetValue = /*payload.datasetValue.length == 0 ? this.state.data : */ payload.datasetValue;
            }
        },
        setDatasetDescription(state, payload) {
            this.state.datasetDescription = payload;
        },
    },
    actions: {
        loadUser({
            commit
        }, user) {
            http
                .login(user)
                .then(response => {
                    commit('setUser', response)
                })
                .catch(error => console.log(error));
        },
        logOut({
            commit
        }) {
            http
                .logOut()
                .then(response => {
                    commit('logOut', response);
                })
                .catch(error => console.log(error));
        },
        loadDataset({
            commit
        }, dataset) {
            http
                .loadDataset(dataset.datasetName, this.state.applicationName)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('setDataset', {
                                datasetValue: data,
                                datasetName: dataset.datasetName,
                                datasetDescription: dataset.datasetDescription
                            });
                        })
                    } else {
                        commit('setDataset', response)
                    }
                })
                .catch(error => console.log(error));
        },
        loadReference({
            commit
        }, reference) {
            http
                .loadReference(reference.referenceName, this.state.applicationName)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('setReference', {
                                referenceValue: data,
                                referenceName: reference.referenceName,
                                referenceDescription: reference.referenceDescription
                            });
                        })
                    } else {
                        commit('setReference', response);
                    }
                })
                .catch(error => console.log(error));
        },
        downloadReference({
            commit
        }, parameters) {
            http
                .loadReference(parameters.referenceName, this.state.applicationName)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('referenceDownload', data);
                        })
                    } else {
                        commit('referenceDownload', response);
                    }
                })
                .catch(error => console.log(error));
        },
        uploadReference({
            commit
        }, parameters) {
            http
                .uploadReference(parameters.referenceName, this.state.applicationName, parameters.file)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('referenceUploaded', data);
                        })
                    } else {
                        commit('referenceUploaded', response)
                    }
                })
                .catch(error => console.log(error));
        },
        uploadDataset({
            commit
        }, parameters) {
            http
                .uploadDataset(parameters.datasetName, this.state.applicationName, parameters.file)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('datasetUploaded', data);
                        })
                    } else {
                        commit('datasetUploaded', response)
                    }
                })
                .catch(error => console.log(error));
        },

        loadApplications({
            commit
        }) {
            http
                .loadApplications()
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('setApplications', data);
                        })
                    } else {
                        commit('setApplications', response)
                    }
                })
                .catch(error => console.log(error));
        },

        loadApplicationConfiguration({
            commit
        }, applicationName) {
            http
                .loadApplicationConfiguration(applicationName)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('setApplication', data);
                        })
                    } else {
                        commit('setApplication', response)
                    }
                })
                .catch(error => console.log(error));
        },
        loadApplication({
            commit
        }, parameters) {
            http
                .loadApplication(parameters.applicationName, parameters.file)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('applicationLoaded', data);
                        })
                    } else {
                        commit('applicationLoaded', response);
                    }
                })
                .catch(error => console.log(error));
        },
        loadDatasetFilter({
            commit
        }, dataset) {
            http
                .loadDataset(dataset.datasetName, this.state.applicationName)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            response.json().then(function(data) {
                                commit('setDataset', {
                                    datasetValue: data,
                                    datasetName: dataset.datasetName,
                                    datasetDescription: dataset.datasetDescription
                                });
                            })
                        })
                    } else {
                        commit('setDataset', response);
                    }
                })
                .catch(error => console.log(error));
        },
        uploadFile({
            commit
        }, id) {
            http
                .uploadFile(id)
                .then(response => {
                    if (response.ok) {
                        response.json().then(function(data) {
                            commit('fileUpload', data);
                        })
                    } else {
                        commit('fileUpload', response);
                    }
                })
                .catch(error => console.log(error));
        },
    },
    getters: {
        isLogged(state) {
            return state.user != null;
        },
        datasetVariables: (state, getters) => {
            const variables = new Set()
            getters.datasetVariableComponents
                .map(variableComponent => variableComponent.variable)
                .forEach(variable => variables.add(variable))
            return Array.from(variables);
        },
        datasetVariableComponents: (state, getters) => {
            const firstLine = state.datasetValue[0]
            const variables = Object.keys(firstLine)
            const result = []
            variables.forEach(variable => {
                const components = Object.keys(firstLine[variable])
                components.forEach(component => {
                    const id = variable + '_' + component
                    result.push({id, variable, component})
                })
            })
            return result;
        },
        isDataAvailable: state => {
            return state.datasetValue.length > 0
        }
    }
})