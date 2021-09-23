import app from "@/main";
import { Fetcher, LOCAL_STORAGE_LANG } from "./Fetcher";

export class UserPreferencesService extends Fetcher {
    static INSTANCE = new UserPreferencesService();

    constructor() {
        super();
        var lang = localStorage.getItem(LOCAL_STORAGE_LANG) || navigator.language.slice(0, 2) || "fr";
        this.setUserPrefLocale(lang);
    }

    setUserPrefLocale(locale) {
        localStorage.setItem(LOCAL_STORAGE_LANG, locale);
        if (app && app.$i18n) app.$i18n.locale = locale;
    }
}