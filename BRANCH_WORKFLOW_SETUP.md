# Branch workflow setup

Krypton uses one branch-aware GitHub Actions workflow: `.github/workflows/android.yml`.

- It runs for pushes to every branch and for pull requests.
- It supports manual runs through `workflow_dispatch`.
- For the **Run workflow** button to appear, GitHub requires the workflow file to exist on the repository's default branch. Commit it there first.
- Also commit the workflow file to the feature branch you intend to run, then select that branch in the Run workflow dropdown.
- The workflow automatically finds `settings.gradle.kts`, so the project files may be at the repository root or nested one level down.

To use it from a new repository, copy the **contents** of the `KryptonPlayer` folder into the repository (or keep that folder as the project directory), commit, and push. Do not upload the ZIP archive as a single GitHub file.
