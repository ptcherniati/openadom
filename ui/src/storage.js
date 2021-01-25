class Storage {
    constructor() {
        this.container = window.sessionStorage || window.localStorage;
    }
    set(key, value) {
        this.container.setItem(key, value);
    }
    get(key) {
        return this.container.getItem(key);
    }
}
const storage = new Storage();
export {
    storage,
};