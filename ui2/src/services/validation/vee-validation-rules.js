import i18n from "@/i18n";
import { email, required } from "vee-validate/dist/rules";
import { extend } from "vee-validate";
// See https://logaretm.github.io/vee-validate/guide/rules.html
// For list of all availables rules

extend("required", {
  ...required,
  message: i18n.t("validation.invalid-required"),
});

extend("email", {
  ...email,
  message: i18n.t("validation.invalid-email"),
});
