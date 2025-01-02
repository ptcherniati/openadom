import os

def generate_pdf_links(directory):
    pdf_links = {}
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(('.md', '.qmd')):
                file_path = os.path.join(root, file)
                relative_path = os.path.relpath(file_path, directory)
                pdf_name = relative_path.replace(os.path.sep, '__').replace('.md', '.pdf').replace('.qmd', '.pdf')
                pdf_links[file_path] = pdf_name
    return pdf_links


def add_pdf_links(directory):
    pdf_links = generate_pdf_links(directory)
    for file_path, pdf_name in pdf_links.items():
        with open(file_path, 'r+', encoding='utf-8') as f:
            content = f.read()
            if f"[Voir la version PDF]({pdf_name})" not in content:
                f.seek(0, 2)
                f.write(f"\n\n[Voir la version PDF]({pdf_name})")
                print(f"Lien ajouté à {file_path}")

directory = 'fichiers'
add_pdf_links(directory)
