export class ApplicationResult {
  id;
  name;
  title;
  comment;
  references = {
    idRef: {
      id: "",
      label: "",
      children: [],
      columns: {
        id: "",
        title: "",
        key: false,
        linkedTo: "",
      },
    },
  };
}
