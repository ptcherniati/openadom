import { Fetcher } from "./Fetcher";

export class TagService extends Fetcher {
    static INSTANCE = new TagService();
    static HIDDEN_TAG = "__hidden__";

    toBeShown(tags, datas) {
        if (!tags) {
            return datas;
        }
        let selectedTags = Object.keys(tags).filter((t) => {
            return tags[t].selected
        });
        if (!Object.keys(tags).length) {
            return datas;
        }
        return datas.filter((data) => {
            return data.tags.some((t) => {
                return selectedTags.includes(t);
            });
        });
    }

    currentTags(tags, currentTags, application, internationalisationService) {
        for (const tagName of currentTags) {
            if (tagName !== "__hidden__") {
                if (tags[tagName]) {
                    continue;
                }
                tags[tagName] = {};
                tags[tagName].selected = true;
                tags[tagName].localName = internationalisationService.getLocaleforPath(
                    application,
                    "internationalizedTags." + tagName,
                    tagName
                );
            }
        }
        return tags;
    }
}