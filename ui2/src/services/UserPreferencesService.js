import app from "@/main";
import { Locales } from "@/utils/LocaleUtils";
import { Fetcher } from "./Fetcher";

const LOCAL_STORAGE_LANG = "lang";

export class UserPreferencesService extends Fetcher {
  static INSTANCE = new UserPreferencesService();

  constructor() {
    super();
  }

  getUserPrefLocale() {
    const browserLocale = window.navigator.language.substring(0, 2);

    return (
      localStorage.getItem(LOCAL_STORAGE_LANG) ||
      (Object.values(Locales).includes(browserLocale) && browserLocale) ||
      Locales.FRENCH
    );
  }

  setUserPrefLocale(locale) {
    localStorage.setItem(LOCAL_STORAGE_LANG, locale);
    app.$i18n.locale = locale;
  }
}
