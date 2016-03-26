package filodb.core.store

import java.nio.ByteBuffer
import scala.concurrent.Future

import filodb.core._
import filodb.core.metadata.{Column, DataColumn, Dataset}

import org.scalatest.{FunSpec, Matchers, BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.concurrent.ScalaFutures

trait MetaStoreSpec extends FunSpec with Matchers
with BeforeAndAfter with BeforeAndAfterAll with ScalaFutures {
  import MetaStore._

  def metaStore: MetaStore
  implicit def defaultPatience: PatienceConfig

  override def beforeAll() {
    super.beforeAll()
    metaStore.initialize("unittest").futureValue(defaultPatience)
  }

  val fooRef = DatasetRef("foo")

  before { metaStore.clearAllData("unittest").futureValue(defaultPatience) }

  describe("dataset API") {
    it("should create a new Dataset if one not there") {
      val dataset = Dataset("foo", Seq("key1", ":getOrElse key2 --"), "seg",
                            Seq("part1", ":getOrElse part2 00"))
      metaStore.newDataset(dataset).futureValue should equal (Success)

      metaStore.getDataset(fooRef).futureValue should equal (dataset)
    }

    it("should return AlreadyExists if dataset already exists") {
      val dataset = Dataset("foo", "autoid", "seg")
      metaStore.newDataset(dataset).futureValue should equal (Success)
      metaStore.newDataset(dataset).futureValue should equal (AlreadyExists)
    }

    it("should return NotFound if getDataset on nonexisting dataset") {
      metaStore.getDataset(DatasetRef("notThere")).failed.futureValue shouldBe a [NotFoundError]
    }

    it("should return all datasets created") {
      for { i <- 0 to 2 } {
        val dataset = Dataset(i.toString, Seq("key1", ":getOrElse key2 --"), "seg",
                              Seq("part1", ":getOrElse part2 00"))
        metaStore.newDataset(dataset).futureValue should equal (Success)
      }

      metaStore.getAllDatasets("unittest").futureValue.toSet should equal (Set("0", "1", "2"))
    }
  }

  describe("column API") {
    it("should return IllegalColumnChange if an invalid column addition submitted") {
      val firstColumn = DataColumn(0, "first", "foo", 1, Column.ColumnType.StringColumn)
      whenReady(metaStore.newColumn(firstColumn, fooRef)) { response =>
        response should equal (Success)
      }

      whenReady(metaStore.newColumn(firstColumn.copy(version = 0), fooRef).failed) { err =>
        err shouldBe an [IllegalColumnChange]
      } (patienceConfig)
    }

    val monthYearCol = DataColumn(1, "monthYear", "gdelt", 1, Column.ColumnType.LongColumn)
    val gdeltRef = DatasetRef("gdelt")

    it("should be able to create a Column and get the Schema") {
      metaStore.newColumn(monthYearCol, gdeltRef).futureValue should equal (Success)
      metaStore.getSchema(gdeltRef, 10).futureValue should equal (Map("monthYear" -> monthYearCol))
    }

    it("deleteDatasets should delete both dataset and columns") {
      val dataset = Dataset("gdelt", "autoid", "seg")
      metaStore.newDataset(dataset).futureValue should equal (Success)
      metaStore.newColumn(monthYearCol, gdeltRef).futureValue should equal (Success)

      metaStore.deleteDataset(gdeltRef).futureValue should equal (Success)
      metaStore.getDataset(gdeltRef).failed.futureValue shouldBe a [NotFoundError]
      metaStore.getSchema(gdeltRef, 10).futureValue should equal (Map.empty)
    }
  }
}