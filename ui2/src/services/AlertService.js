import { i18n } from "@/main";
import { BuefyTypes } from "@/utils/BuefyUtils";
import { ToastProgrammatic, DialogProgrammatic } from "buefy";

const TOAST_INFO_DURATION = 3000;
const TOAST_ERROR_DURATION = 8000;
const TOAST_POSITION = "is-top";

/**
 * Un service pour gérer les différents messages d'alerte et popup d'info s'affichant sur l'application
 */
export class AlertService {
  static INSTANCE = new AlertService();

  toastSuccess(message) {
    ToastProgrammatic.open({
      message: message,
      type: BuefyTypes.SUCCESS,
      duration: TOAST_INFO_DURATION,
      position: TOAST_POSITION,
    });
  }

  toastWarn(message, error) {
    console.warn("[WARNING] " + message, error);
    ToastProgrammatic.open({
      message: message,
      type: BuefyTypes.WARNING,
      duration: TOAST_ERROR_DURATION,
      position: TOAST_POSITION,
    });
  }

  toastError(message, error) {
    console.error("[ERROR] " + message, error);
    ToastProgrammatic.open({
      message: message,
      type: BuefyTypes.DANGER,
      duration: TOAST_ERROR_DURATION,
      position: TOAST_POSITION,
    });
  }

  toastServerError(error) {
    if (error.content != null) {
      if (error.content.message.indexOf("exist")) {
        this.toastError(i18n.t("alert.server-error-appli-exist"), error);
      }
    } else {
      this.toastError(i18n.t("alert.server-error"), error);
    }
  }

  dialog(title, message, confirmText, type, onConfirmCb) {
    DialogProgrammatic.confirm({
      title: title,
      message: message,
      confirmText: confirmText,
      type: type,
      hasIcon: true,
      cancelText: this.cancelMsg,
      onConfirm: () => {
        onConfirmCb();
      },
    });
  }
}
