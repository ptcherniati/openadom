import app from "@/main";
import { Fetcher, LOCAL_STORAGE_LANG } from "./Fetcher";

export class UserPreferencesService extends Fetcher {
  static INSTANCE = new UserPreferencesService();

  constructor() {
    super();
  }

  setUserPrefLocale(locale) {
    localStorage.setItem(LOCAL_STORAGE_LANG, locale);
    app.$i18n.locale = locale;
  }
}
